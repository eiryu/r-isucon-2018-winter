package main

import (
	"database/sql"
	"encoding/gob"
	"encoding/json"
	"fmt"
	"html/template"
	"io"
	"io/ioutil"
	"net/http"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	_ "github.com/go-sql-driver/mysql"
	"github.com/google/uuid"
	"github.com/gorilla/sessions"
	"github.com/gorilla/websocket"
	"github.com/jameskeane/bcrypt"
	"github.com/jmoiron/sqlx"
	"github.com/labstack/echo"
	"github.com/labstack/echo-contrib/session"
	"github.com/labstack/echo/middleware"
	"github.com/pkg/errors"
)

var (
	db       *sqlx.DB
	upgrader = websocket.Upgrader{}
)

type TemplateRenderer struct {
	templates *template.Template
}

func (t *TemplateRenderer) Render(w io.Writer, name string, data interface{}, c echo.Context) error {
	return t.templates.ExecuteTemplate(w, name, data)
}

type User struct {
	Username  string
	Salt      string
	Hash      string
	Icon      string
	LastName  string
	FirstName string
}
type Group struct {
	ID        int
	Name      string
	Owner     string
	UserCount int
	ChatCount int
}
type Chat struct {
	ID          int
	Comment     string
	CommentBy   string    `db:"comment_by"`
	CommentAt   time.Time `db:"comment_at"`
	CommentUser *User
	Count       int
}
type BelongsUserGroup struct {
	GroupID  int `db:"group_id"`
	Username string
}

func getUser(queryer sqlx.Queryer, username string) (*User, error) {
	var u User
	err := sqlx.Get(queryer, &u, "SELECT * FROM user WHERE username = ?", username)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	return &u, nil
}
func addUser(execer sqlx.Execer, username string, password string, lastname string, firstname string, filename string) error {
	salt, _ := bcrypt.Salt(10)
	hash, _ := bcrypt.Hash(password, salt)
	_, err := execer.Exec("INSERT INTO user (username, salt, hash, lastname, firstname, icon) VALUES (?, ?, ?, ?, ?, ?)",
		username, salt, hash, lastname, firstname, filename)
	return err
}
func getGroup(queryer sqlx.Queryer, groupname string, owner string) (*Group, error) {
	var g Group
	err := sqlx.Get(queryer, &g, "SELECT * FROM groups WHERE name = ? and owner = ?", groupname, owner)
	return &g, err
}
func getGroups(queryer sqlx.Queryer, username string) ([]*Group, error) {
	var gs []*Group

	var bugs []*BelongsUserGroup
	err := sqlx.Select(queryer, &bugs, "SELECT * FROM belongs_user_group WHERE username = ? ORDER BY group_id", username)
	if err != nil {
		return nil, err
	}
	for _, bug := range bugs {
		var g Group
		err := sqlx.Get(queryer, &g, "SELECT * FROM groups WHERE id = ?", bug.GroupID)
		if err != nil {
			return nil, errors.Wrapf(err, "failed to get group(id=%v)", bug.GroupID)
		}
		err = queryer.QueryRowx("SELECT COUNT(*) as cnt FROM belongs_user_group WHERE group_id = ?", bug.GroupID).Scan(&g.UserCount)
		if err != nil {
			return nil, errors.Wrapf(err, "failed to count user (group_id=%v)", bug.GroupID)
		}
		err = queryer.QueryRowx("SELECT COUNT(*) as cnt FROM belongs_chat_group WHERE group_id = ?", bug.GroupID).Scan(&g.ChatCount)
		if err != nil {
			return nil, errors.Wrapf(err, "failed to count chat (group_id=%v)", bug.GroupID)
		}
		gs = append(gs, &g)
	}
	return gs, nil
}
func getChats(groupID int) ([]*Chat, error) {
	var chatIDs []int
	err := db.Select(&chatIDs, "SELECT chat_id FROM belongs_chat_group WHERE group_id = ? ORDER BY chat_id DESC LIMIT 100", groupID)
	if err == sql.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, err
	}
	if len(chatIDs) == 0 {
		return nil, nil
	}
	var cs []*Chat
	query, args, err := sqlx.In("SELECT * FROM chat WHERE id IN (?) ORDER BY comment_at", chatIDs)
	if err != nil {
		return nil, err
	}
	err = db.Select(&cs, query, args...)
	return cs, err
}
func getBelongsUserGroup(groupID int, username string) (*BelongsUserGroup, error) {
	var bug BelongsUserGroup
	err := db.Get(&bug, "SELECT * FROM belongs_user_group WHERE group_id = ? AND username = ?", groupID, username)
	return &bug, err
}
func addBelongsUserGroup(execer sqlx.Execer, groupID int, username string) error {
	_, err := execer.Exec("INSERT INTO belongs_user_group (group_id, username) VALUES (?, ?) ON DUPLICATE KEY UPDATE group_id = ?, username = ?",
		groupID, username, groupID, username)
	return err
}
func addChatRead(execer sqlx.Execer, chatID int, username string) error {
	_, err := execer.Exec("INSERT INTO read_chat (chat_id, username) VALUES (?, ?) ON DUPLICATE KEY UPDATE chat_id = ?, username = ?", chatID, username, chatID, username)
	return err
}
func getChatRead(queryer sqlx.Queryer, chatID int) (int, error) {
	var chatCount int
	err := queryer.QueryRowx("SELECT COUNT(*) as cnt FROM read_chat WHERE chat_id = ?", chatID).Scan(&chatCount)
	if err == sql.ErrNoRows {
		return 0, nil
	}
	if err != nil {
		return 0, err
	}
	return chatCount, nil
}
func getBelongsChatGroup(queryer sqlx.Queryer, groupID, chatID int) (int, error) {
	var cid int
	err := queryer.QueryRowx("SELECT chat_id FROM belongs_chat_group WHERE group_id = ? AND chat_id = ?", groupID, chatID).Scan(&cid)
	return cid, err
}

func currentUser(c echo.Context) *User {
	s, _ := session.Get("RINE_SESSION", c)
	user, ok := s.Values["user"]
	if !ok {
		return nil
	}
	return user.(*User)
}
func setUser(c echo.Context, username string) {
	s, _ := session.Get("RINE_SESSION", c)
	if username == "" {
		delete(s.Values, "user")
	} else {
		user, err := getUser(db, username)
		if err != nil {
			c.Logger().Errorf("failed to get user(username=%v): %v", username, err)
			return
		}
		s.Values["user"] = user
	}
	if err := s.Save(c.Request(), c.Response()); err != nil {
		c.Logger().Errorf("failed to save user to the session(username=%v): %v", username, err)
	}
}

func validateRegister(username string, password string) (bool, string) {
	switch {
	case username == "":
		return false, "ユーザ名がありません。"
	case len(username) < 5 || len(username) > 30:
		return false, "ユーザ名は5文字以上30文字以下にしてください。"
	case !regexp.MustCompile(`(?i)^[a-z0-9_]+$`).MatchString(username):
		return false, "ユーザ名はアルファベットか数字にしてください。"
	case password == "":
		return false, "パスワードがありません。"
	case len(password) < 5 || len(password) > 30:
		return false, "パスワードは5文字以上30文字以下にしてください。"
	case !regexp.MustCompile(`(?i)^[a-z0-9_]+$`).MatchString(password):
		return false, "パスワードはアルファベットか数字にしてください。"
	case strings.Contains(password, username):
		return false, "パスワードにはユーザ名を含めないでください。"
	default:
		return true, ""
	}
}

func getEnv(key, defaults string) string {
	x := os.Getenv(key)
	if x == "" {
		return defaults
	}
	return x
}

type SocketEntry struct {
	conn *websocket.Conn
	id   int
}

func main() {
	gob.Register(&User{})

	dbHost := getEnv("RISUCON_DB_HOST", "127.0.0.1")
	dbPort := getEnv("RISUCON_DB_PORT", "3306")
	dbUser := getEnv("RISUCON_DB_USER", "isucon")
	dbPassword := getEnv("RISUCON_DB_PASSWORD", "isucon")

	if dbPassword != "" {
		dbUser += ":" + dbPassword
	}
	dsn := fmt.Sprintf("%s@tcp(%s:%s)/rine?parseTime=true&loc=Local&charset=utf8mb4&multiStatements=true", dbUser, dbHost, dbPort)
	db = sqlx.MustOpen("mysql", dsn)

	e := echo.New()
	e.Renderer = &TemplateRenderer{
		templates: template.Must(template.ParseGlob("views/*.tmpl")),
	}
	e.Use(middleware.Logger())
	e.Use(middleware.Recover())
	e.Use(session.Middleware(sessions.NewCookieStore([]byte("rine-secret-key"))))
	e.Static("/", "public")

	var sockets []*SocketEntry
	var socketID int
	var socketsMu sync.Mutex

	e.GET("/", func(c echo.Context) error {
		return c.Redirect(http.StatusSeeOther, "/groups")
	})

	e.GET("/initialize", func(c echo.Context) error {
		if err := exec.Command("../sql/db_setup.sh").Run(); err != nil {
			return errors.Wrap(err, "failed to execute initialize query")
		}
		return c.JSON(http.StatusOK, echo.Map{"message": "initialized"})
	})

	e.GET("/register", func(c echo.Context) error {
		if currentUser(c) != nil {
			return c.Redirect(http.StatusSeeOther, "/")
		}
		return c.Render(http.StatusOK, "register.tmpl", echo.Map{})
	})

	e.POST("/register", func(c echo.Context) error {
		filename := "default.png"
		username := c.FormValue("username")
		password := c.FormValue("password")
		lastname := c.FormValue("lastname")
		firstname := c.FormValue("firstname")

		fh, err := c.FormFile("icon")
		if err != http.ErrMissingFile && err != http.ErrNotMultipart && err != nil {
			return errors.Wrap(err, "failed to get icon")
		}
		if err == nil {
			filename = fmt.Sprintf("%s-%d.%s%s", username, time.Now().UnixNano()/1000000, uuid.New().String(), filepath.Ext(fh.Filename))
			f, err := fh.Open()
			if err != nil {
				return errors.Wrap(err, "failed to open icon")
			}
			b, err := ioutil.ReadAll(f)
			if err != nil {
				return errors.Wrap(err, "failed to read icon")
			}
			err = ioutil.WriteFile("./public/images/"+filename, b, os.FileMode(0644))
			if err != nil {
				return errors.Wrap(err, "failed to save icon")
			}
		}
		if ok, msg := validateRegister(username, password); !ok {
			return c.Render(http.StatusBadRequest, "register.tmpl", echo.Map{"message": msg})
		}

		user, err := getUser(db, username)
		if err != nil {
			return errors.Wrapf(err, "failed to check user(username=%v)", username)
		}
		if user != nil {
			return c.Render(http.StatusForbidden, "register.tmpl", echo.Map{"message": "既にユーザーが登録されています。"})
		}

		err = addUser(db, username, password, lastname, firstname, filename)
		if err != nil {
			return errors.Wrapf(err, "failed to add user(username=%v)", username)
		}
		general, err := getGroup(db, "general", "root")
		if err != nil {
			return errors.Wrap(err, "failed to get group(general)")
		}
		err = addBelongsUserGroup(db, general.ID, username)
		if err != nil {
			return errors.Wrap(err, "failed to add belong_user_group")
		}

		setUser(c, username)

		return c.Redirect(http.StatusSeeOther, "/")
	})

	e.GET("/login", func(c echo.Context) error {
		if currentUser(c) != nil {
			return c.Redirect(http.StatusSeeOther, "/")
		}
		return c.Render(http.StatusOK, "login.tmpl", echo.Map{})
	})

	e.POST("/login", func(c echo.Context) error {
		username := c.FormValue("username")
		password := c.FormValue("password")
		user, err := getUser(db, username)
		if user == nil || err != nil {
			return c.Render(http.StatusBadRequest, "login.tmpl", echo.Map{"message": "ユーザ名もしくはパスワードが間違っています。"})
		}

		if hash, _ := bcrypt.Hash(password, user.Salt); hash != user.Hash {
			return c.Render(http.StatusBadRequest, "login.tmpl", echo.Map{"message": "ユーザ名もしくはパスワードが間違っています。"})
		}
		setUser(c, username)
		return c.Redirect(http.StatusSeeOther, "/")
	})

	e.POST("/logout", func(c echo.Context) error {
		setUser(c, "")
		return c.Redirect(http.StatusSeeOther, "/")
	})

	e.GET("/ws/notification", func(c echo.Context) error {
		ws, err := upgrader.Upgrade(c.Response(), c.Request(), nil)
		if err != nil {
			return errors.Wrap(err, "failed to upgrade websocket")
		}
		defer ws.Close()

		socketsMu.Lock()
		sid := socketID
		socketID++
		sockets = append(sockets, &SocketEntry{
			conn: ws,
			id:   sid,
		})
		socketsMu.Unlock()

		for {
			_, _, err = ws.ReadMessage()
			if err != nil {
				break
			}
		}

		socketsMu.Lock()
		s := len(sockets)
		for i := 0; i < s; i++ {
			if sockets[i].id == sid {
				sockets[i] = nil
				sockets[i] = sockets[s-1]
				sockets = sockets[:s-1]
				break
			}
		}
		socketsMu.Unlock()

		return nil
	})

	e.GET("/groups", func(c echo.Context) error {
		if currentUser(c) == nil {
			return c.Redirect(http.StatusSeeOther, "/login")
		}
		groups, err := getGroups(db, currentUser(c).Username)
		if err != nil {
			return errors.Wrap(err, "failed to get groups")
		}
		return c.Render(http.StatusOK, "groups.tmpl", echo.Map{"user": currentUser(c), "groups": groups})
	})

	e.POST("/groups", func(c echo.Context) error {
		user := currentUser(c)
		if user == nil {
			return c.Redirect(http.StatusSeeOther, "/login")
		}
		groups, err := getGroups(db, user.Username)
		if err != nil {
			return errors.Wrap(err, "failed to get groups")
		}

		writeMessage := func(status int, msg string) error {
			return c.Render(status, "groups.tmpl", echo.Map{"user": currentUser(c), "message": msg, "groups": groups})
		}
		writeBad := func(msg string) error {
			return writeMessage(http.StatusBadRequest, msg)
		}
		writeError := func(err error) error {
			return writeMessage(http.StatusInternalServerError, err.Error())
		}

		groupName := c.FormValue("groupname")
		ok, msg := func() (bool, string) {
			switch {
			case groupName == "":
				return false, "グループ名がありません。"
			case len(groupName) < 2 || 20 < len(groupName):
				return false, "グループ名は2文字以上20文字以下にしてください。"
			default:
				return true, ""
			}
		}()

		if !ok {
			return writeBad(msg)
		}

		tx, err := db.Beginx()
		if err != nil {
			return errors.Wrap(err, "failed to start groups tx")
		}
		defer tx.Rollback()

		_, err = tx.Exec("INSERT INTO groups (name, owner) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = ?, owner = ?", groupName, user.Username, groupName, user.Username)
		if err != nil {
			return writeError(errors.Wrap(err, "failed to insert group"))
		}

		var groupID int
		err = tx.QueryRow("SELECT id FROM groups WHERE name = ? AND owner = ?", groupName, user.Username).Scan(&groupID)
		if err != nil {
			return writeError(errors.Wrapf(err, "failed to find group by name(%v) and owner(%v)", groupName, user.Username))
		}

		targetUsernames := []string{
			user.Username,
		}
		for _, name := range strings.Split(c.FormValue("usernames"), ",") {
			trimmed := strings.TrimSpace(name)
			if trimmed == "" {
				continue
			}
			targetUsernames = append(targetUsernames, trimmed)
		}

		for _, name := range targetUsernames {
			u, err := getUser(tx, name)
			if err != nil {
				return writeError(errors.Wrapf(err, "failed to find user(name=%v)", name))
			}
			if u == nil {
				return writeBad("存在しないユーザが指定されています")
			}
			err = addBelongsUserGroup(tx, groupID, name)
			if err != nil {
				return writeError(errors.Wrap(err, "failed to add belongs_user_group"))
			}
		}

		err = tx.Commit()
		if err != nil {
			return writeError(errors.Wrap(err, "failed to commit groups tx"))
		}

		return c.Redirect(http.StatusSeeOther, "/groups")
	})

	e.GET("/groups/:owner/:groupname", func(c echo.Context) error {
		if currentUser(c) == nil {
			return c.Redirect(http.StatusSeeOther, "/login")
		}
		owner := c.Param("owner")
		groupName := c.Param("groupname")

		group, err := getGroup(db, groupName, owner)
		if err != nil {
			return c.Render(http.StatusNotFound, "chat.tmpl", echo.Map{"user": currentUser(c), "message": "no found group"})
		}
		_, err = getBelongsUserGroup(group.ID, currentUser(c).Username)
		if err != nil {
			return c.Render(http.StatusNotFound, "chat.tmpl", echo.Map{"user": currentUser(c), "message": "no found group"})
		}

		chats, err := getChats(group.ID)
		if err != nil {
			return errors.Wrapf(err, "failed to get chats(group_id=%v)", group.ID)
		}
		if len(chats) == 0 {
			return c.Render(http.StatusOK, "chat.tmpl", echo.Map{"user": currentUser(c), "title": groupName, "chats": chats})
		}

		for _, chat := range chats {
			commentUser, err := getUser(db, chat.CommentBy)
			if err != nil {
				return errors.Wrapf(err, "failed to get user(comment_by=%v)", chat.CommentBy)
			}
			chat.CommentUser = commentUser
			_ = addChatRead(db, chat.ID, currentUser(c).Username)
			count, err := getChatRead(db, chat.ID)
			if err != nil {
				return errors.Wrapf(err, "failed to count chat")
			}

			socketsMu.Lock()
			for _, socket := range sockets {
				_ = socket.conn.WriteJSON(map[string]interface{}{
					"eventName": "read",
					"id":        chat.ID,
					"username":  currentUser(c).Username,
					"count":     count,
				})
			}
			socketsMu.Unlock()
			chat.Count, err = getChatRead(db, chat.ID)
		}
		return c.Render(http.StatusOK, "chat.tmpl", echo.Map{"user": currentUser(c), "title": groupName, "chats": chats})
	})

	e.POST("/groups/:owner/:groupname", func(c echo.Context) error {
		if currentUser(c) == nil {
			return c.JSON(http.StatusUnauthorized, echo.Map{"message": "unauthorized"})
		}
		groupName := c.Param("groupname")
		owner := c.Param("owner")
		group, err := getGroup(db, groupName, owner)
		if err == sql.ErrNoRows {
			return c.JSON(http.StatusNotFound, echo.Map{"message": "not found group"})
		}
		if err != nil {
			return errors.Wrapf(err, "failed to get group(name=%v, owner=%v)", groupName, owner)
		}

		_, err = getBelongsUserGroup(group.ID, currentUser(c).Username)
		if err == sql.ErrNoRows {
			return c.JSON(http.StatusNotFound, echo.Map{"message": "not found group"})
		}
		if err != nil {
			return errors.Wrapf(err, "failed to get belongs_user_group(group_id=%v, username=%v)", group.ID, currentUser(c).Username)
		}

		tx, err := db.Beginx()
		if err != nil {
			return errors.Wrapf(err, "failed to get chat transaction")
		}
		defer tx.Rollback()

		var req struct {
			Comment string `json:"value"`
		}
		b, err := ioutil.ReadAll(c.Request().Body)
		if err != nil {
			return errors.Wrapf(err, "failed to read chat body")
		}
		if err := json.Unmarshal(b, &req); err != nil {
			return errors.Wrapf(err, "failed to unmarshal chat payload")
		}

		_, err = tx.Exec("INSERT INTO chat (comment, comment_by, comment_at) VALUES (?, ?, ?)", req.Comment, currentUser(c).Username, time.Now())
		if err != nil {
			return errors.Wrapf(err, "failed to insert chat")
		}
		var lastID int
		err = tx.QueryRowx("SELECT LAST_INSERT_ID() as id FROM chat").Scan(&lastID)
		if err != nil {
			return errors.Wrapf(err, "failed to get last insert id on chat")
		}
		_, err = tx.Exec("INSERT INTO belongs_chat_group (chat_id, group_id) VALUES (?, ?)", lastID, group.ID)
		if err != nil {
			return errors.Wrapf(err, "failed to insert belongs_chat_group(chat_id=%v, group_id=%v)", lastID, group.ID)
		}
		socketsMu.Lock()
		for _, socket := range sockets {
			_ = socket.conn.WriteJSON(map[string]interface{}{
				"eventName": "message",
				"comment":   req.Comment,
				"commentBy": map[string]interface{}{
					"username": currentUser(c).Username,
					"icon":     currentUser(c).Icon,
				},
				"groupname": groupName,
				"chatId":    lastID,
				"count":     0,
			})
		}
		socketsMu.Unlock()

		if err := tx.Commit(); err != nil {
			return errors.Wrapf(err, "failed to commit insert comment")
		}

		return c.JSON(http.StatusOK, echo.Map{"message": "ok", "chatId": lastID})
	})

	e.POST("/groups/:owner/:groupname/:chatid", func(c echo.Context) error {
		if currentUser(c) == nil {
			return c.JSON(http.StatusUnauthorized, echo.Map{"message": "unauthorized"})
		}

		chatID, err := strconv.Atoi(c.Param("chatid"))
		if err != nil {
			return errors.Wrapf(err, "failed to parse chat id")
		}
		group, err := getGroup(db, c.Param("groupname"), c.Param("owner"))
		if err == sql.ErrNoRows {
			return c.JSON(http.StatusNotFound, echo.Map{"message": "not found chat"})
		}
		if err != nil {
			return errors.Wrapf(err, "failed to get group")
		}
		_, err = getBelongsUserGroup(group.ID, currentUser(c).Username)
		if err == sql.ErrNoRows {
			return c.JSON(http.StatusNotFound, echo.Map{"message": "not found chat"})
		}
		if err != nil {
			return errors.Wrapf(err, "failed to get belongs_user_group")
		}
		_, err = getBelongsChatGroup(db, group.ID, chatID)
		if err == sql.ErrNoRows {
			return c.JSON(http.StatusNotFound, echo.Map{"message": "not found chat"})
		}

		_ = func() error {
			err := addChatRead(db, chatID, currentUser(c).Username)
			if err != nil {
				return errors.Wrapf(err, "failed to add chat read")
			}
			count, err := getChatRead(db, chatID)
			if err != nil {
				return errors.Wrapf(err, "failed to get chat read")
			}
			socketsMu.Lock()
			for _, socket := range sockets {
				_ = socket.conn.WriteJSON(map[string]interface{}{
					"eventName": "read",
					"id":        chatID,
					"username":  currentUser(c).Username,
					"count":     count,
				})
			}
			socketsMu.Unlock()
			return nil
		}()

		return c.JSON(http.StatusOK, echo.Map{"message": "ok", "id": chatID})
	})

	e.Start(":3000")
}

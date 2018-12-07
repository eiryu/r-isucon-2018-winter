require 'securerandom'
require 'bcrypt'
require 'mysql2'
require 'sinatra/base'
require 'sinatra/json'
require 'faye/websocket'

Faye::WebSocket.load_adapter('thin')

class App < Sinatra::Base
  configure do
    use Rack::Session::Pool,
      key: 'RINE_SESSION',
      secret: 'rine-secret-key',
      httponly: false
    set :public_folder, File.expand_path('../public', __FILE__)
    set :sockets, []
  end

  configure :development do
    require 'sinatra/reloader'
    register Sinatra::Reloader
  end

  helpers do
    def user
      return @_user unless @_user.nil?

      @_user = session[:user]
    end
  end

  get '/' do
    redirect '/groups'
  end

  get '/initialize' do
    db_initialize
    json message: 'initialized'
  end

  get '/register' do
    if user
      return redirect '/'
    end

    genErb :register
  end

  post '/register' do
    filename = 'default.png'
    if params[:icon]
      filename = "#{params[:username]}-#{Time.now.strftime('%s%L').to_i}.#{SecureRandom.uuid}#{File.extname(params[:icon][:filename])}"
      File.open "#{settings.public_folder}/images/#{filename}", 'wb' do |f|
        f.write params[:icon][:tempfile].read
      end
    end

    error = -> username, password do
      if !username
        return 'ユーザ名がありません。'
      elsif username.length < 5 || username.length > 30
        return 'ユーザ名は5文字以上30文字以下にしてください。'
      elsif username !~ /^[a-z0-9_]+$/i
        return 'ユーザ名はアルファベットか数字にしてください。'
      elsif !password
        return 'パスワードがありません。'
      elsif password.length < 5 || password.length > 30
        return 'パスワードは5文字以上30文字以下にしてください。'
      elsif password !~ /^[a-z0-9_]+$/i
        return 'パスワードはアルファベットか数字にしてください。'
      elsif password.include? username
        return 'パスワードにはユーザ名を含めないでください。'
      end
    end.call params[:username], params[:password]

    if error
      status 400
      return genErb :register, message: error
    end

    if db_get_user(params[:username])
      status 403
      return genErb :register, message: '既にユーザーが登録されています。'
    end

    db_add_user(params[:username], params[:password], params[:lastname], params[:firstname], filename)
    general = db_get_group('general', 'root')
    db_add_belongs_user_group(general['id'], params[:username])

    session[:user] = db_get_user(params[:username])
    redirect '/'
  end

  get '/login' do
    if user
      return redirect '/'
    end

    genErb :login
  end

  post '/login' do
    user = db_get_user(params[:username])
    unless user
      status 400
      return genErb :login, message: 'ユーザ名もしくはパスワードが間違っています。'
    end
    unless user['hash'] == BCrypt::Engine.hash_secret(params[:password], user['salt'])
      status 400
      return genErb :login, message: 'ユーザ名もしくはパスワードが間違っています。'
    end
    session[:user] = user
    redirect '/'
  end

  post '/logout' do
    session[:user] = nil
    redirect '/'
  end

  get '/ws/notification' do
    ws = Faye::WebSocket.new(request.env)
    ws.on :open do
      settings.sockets << ws
    end
    ws.on :close do
      settings.sockets.delete(ws)
    end
    ws.rack_response
  end

  get '/groups' do
    unless user
      return redirect '/login'
    end

    groups = db_get_groups(user['username'])
    genErb :groups, groups: groups
  end

  post '/groups' do
    unless user
      return redirect '/login'
    end

    groups = db_get_groups(user['username'])

    error = -> groupname do
      if !groupname
        return 'グループ名がありません。'
      elsif groupname.length < 2 || groupname.length > 20
        return 'グループ名は2文字以上20文字以下にしてください。'
      end
    end.call params[:groupname]

    if error
      status 400
      return genErb :groups, message: error, groups: groups
    end

    db.query('BEGIN')
    begin
      statement = db.prepare('INSERT INTO groups (name, owner) VALUES (?, ?) ON DUPLICATE KEY UPDATE name = ?, owner = ?')
      statement.execute(params[:groupname], user['username'], params[:groupname], user['username'])
      groupid = begin
        statement = db.prepare('SELECT id FROM groups WHERE name = ? AND owner = ?')
        statement.execute(params[:groupname], user['username']).first['id']
      end
      target_usernames = params[:usernames].split(',').map(&:strip).reject(&:empty?).push(user['username'])
      target_usernames.each do |username|
        unless db_get_user(username)
          raise UserNotFoundError
        end
        db_add_belongs_user_group(groupid, username)
      end
    rescue => e
      db.query('ROLLBACK')
      status e.respond_to?(:status) ? e.status : 500
      return genErb :groups, message: e.message, groups: groups
    end
    db.query('COMMIT')
    redirect '/groups'
  end

  get '/groups/:owner/:groupname' do
    unless user
      return redirect '/login'
    end

    group = db_get_group(params[:groupname], params[:owner])
    unless group
      status 404
      return genErb :chat, message: 'not found group', chats: []
    end
    belongs = db_get_belongs_user_group(group['id'], user['username'])
    unless belongs
      status 404
      return genErb :chat, message: 'not found group', chats: []
    end
    chatIds = begin
      statement = db.prepare('SELECT chat_id FROM belongs_chat_group WHERE group_id = ? ORDER BY chat_id DESC LIMIT 100')
      statement.execute(group['id']).map {|row| row['chat_id']}
    end
    if chatIds.empty?
      return genErb :chat, title: params[:groupname], chats: []
    end

    chats = db.query("SELECT * FROM chat WHERE id IN (#{chatIds.join(',')}) ORDER BY comment_at").to_a

    chats.each do |chat|
      chat['commentUser'] = db_get_user(chat['comment_by'])
      begin
        db_add_chat_read(chat['id'], user['username'])
        count = db_get_chat_read(chat['id'])
        data = {
          eventName: 'read',
          id: chat['id'],
          username: user['username'],
          count: count
        }
        settings.sockets.each { |socket| socket.send JSON.generate(data) }
      rescue
        # ignore
      end
      chat['count'] = db_get_chat_read(chat['id'])
    end

    genErb :chat, title: params[:groupname], chats: chats
  end

  post '/groups/:owner/:groupname' do
    unless user
      status 401
      return json message: 'unauthorized'
    end

    group = db_get_group(params[:groupname], params[:owner])
    unless group
      status 404
      return json message: 'not found group'
    end
    belongs = db_get_belongs_user_group(group['id'], user['username'])
    unless belongs
      status 404
      return json message: 'not found group'
    end

    db.query('BEGIN')
    begin
      comment = JSON.parse(request.body.read)['value']
      statement = db.prepare('INSERT INTO chat (comment, comment_by, comment_at) VALUES (?, ?, ?)')
      statement.execute(comment, user['username'], Time.now)
      lastId = db.query('SELECT LAST_INSERT_ID() as id FROM chat').first['id']
      statement = db.prepare('INSERT INTO belongs_chat_group (chat_id, group_id) VALUES (?, ?)')
      statement.execute(lastId, group['id'])
      data = {
        eventName: 'message',
        comment: comment,
        commentBy: {
          username: user['username'],
          icon: user['icon']
        },
        groupname: params[:groupname],
        chatId: lastId,
        count: 0
      }
      settings.sockets.each {|s| s.send JSON.generate data}
    rescue => e
      db.query('ROLLBACK')
      status 500
      return json message: 'error'
    end
    db.query('COMMIT')

    json message: 'ok', chatId: lastId
  end

  post '/groups/:owner/:groupname/:chatid' do
    unless user
      status 401
      return json message: 'unauthorized'
    end

    chatid = params[:chatid].to_i
    group = db_get_group(params[:groupname], params[:owner])
    unless group
      status 404
      return json message: 'not found chat'
    end
    belongs = db_get_belongs_user_group(group['id'], user['username'])
    unless belongs
      status 404
      return json message: 'not found chat'
    end
    chat = db_get_belongs_chat_group(group['id'], chatid)
    unless chat
      status 404
      return json message: 'not found chat'
    end

    begin
      db_add_chat_read(chatid, user['username'])
      count = db_get_chat_read(chatid)
      data = {
        eventName: 'read',
        id: chatid,
        username: user['username'],
        count: count
      }
      settings.sockets.each { |socket| socket.send JSON.generate(data) }
    rescue
      # ignore
    end

    json message: 'ok', id: chatid
  end

  private

  class UserNotFoundError < StandardError
    def status
      400
    end
    def message
      '存在しないユーザが指定されています'
    end
  end

  def genErb(path, params = {})
    params[:title] ||= {
      login: 'ログイン',
      register: '新規ユーザ登録',
      groups: 'グループ'
    }[path]
    erb path, locals: params
  end

  def db
    return @_db unless @_db.nil?

    @_db = Mysql2::Client.new(
      host: ENV.fetch('RISUCON_DB_HOST') { '127.0.0.1' },
      port: ENV.fetch('RISUCON_DB_PORT') { '3306' },
      username: ENV.fetch('RISUCON_DB_USER') { 'isucon' },
      password: ENV.fetch('RISUCON_DB_PASSWORD') { 'isucon' },
      database: 'rine',
      encoding: 'utf8mb4',
      flags: Mysql2::Client::MULTI_STATEMENTS
    )
    @_db.query('SET SESSION sql_mode=\'TRADITIONAL,NO_AUTO_VALUE_ON_ZERO,ONLY_FULL_GROUP_BY\'')
    @_db
  end

  def db_initialize
    system %q(bash ../sql/db_setup.sh)
  end

  def db_get_user(username)
    statement = db.prepare('SELECT * FROM user WHERE username = ?')
    statement.execute(username).first
  end

  def db_add_user(username, password, lastname, firstname, filename)
    salt = BCrypt::Engine.generate_salt
    hash = BCrypt::Engine.hash_secret(params[:password], salt)
    statement = db.prepare('INSERT INTO user (username, salt, hash, lastname, firstname, icon) VALUES (?, ?, ?, ?, ?, ?)')
    statement.execute(username, salt, hash, lastname, firstname, filename)
  end

  def db_get_groups(username)
    statement = db.prepare('SELECT * FROM belongs_user_group WHERE username = ? ORDER BY group_id')
    groups = statement.execute(username).map do |row|
      statement = db.prepare('SELECT * FROM groups WHERE id = ?')
      group = statement.execute(row['group_id']).first
      statement.close
      group['userCount'] = begin
        statement = db.prepare('SELECT COUNT(*) as cnt FROM belongs_user_group WHERE group_id = ?')
        statement.execute(row['group_id']).first['cnt']
      end
      group['chatCount'] = begin
        statement = db.prepare('SELECT COUNT(*) as cnt FROM belongs_chat_group WHERE group_id = ?')
        statement.execute(row['group_id']).first['cnt']
      end
      group
    end
    statement.close
    groups
  end

  def db_get_group(groupname, owner)
    statement = db.prepare('SELECT * FROM groups WHERE name = ? and owner = ?')
    statement.execute(groupname, owner).first
  end

  def db_get_belongs_user_group(group_id, username)
    statement = db.prepare('SELECT * FROM belongs_user_group WHERE group_id = ? AND username = ?')
    statement.execute(group_id, username).first
  end

  def db_add_belongs_user_group(group_id, username)
    statement = db.prepare('INSERT INTO belongs_user_group (group_id, username) VALUES (?, ?) ON DUPLICATE KEY UPDATE group_id = ?, username = ?')
    statement.execute(group_id, username, group_id, username)
  end

  def db_get_belongs_chat_group(group_id, chat_id)
    statement = db.prepare('SELECT chat_id FROM belongs_chat_group WHERE group_id = ? AND chat_id = ?')
    statement.execute(group_id, chat_id).first
  end

  def db_get_chat_read(chat_id)
    statement = db.prepare('SELECT COUNT(*) as cnt FROM read_chat WHERE chat_id = ?')
    statement.execute(chat_id).first['cnt']
  end

  def db_add_chat_read(chat_id, username)
    statement = db.prepare('INSERT INTO read_chat (chat_id, username) VALUES (?, ?) ON DUPLICATE KEY UPDATE chat_id = ?, username = ?')
    statement.execute(chat_id, username, chat_id, username)
  end
end

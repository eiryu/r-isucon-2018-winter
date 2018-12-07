const chatForm = document.getElementById("chat-form");

if (chatForm) {
  const ws = new WebSocket(`ws://${location.host}/ws/notification`)
  const groupname = document.title;
  const username = (document.getElementById("_username") || {}).value;
  const chatList = document.getElementById("chat-list") || {};

  ws.onmessage = (message) => {
    const data = JSON.parse(message.data);

    if (data.eventName === "message" && data.groupname === groupname) {
      appendChat(data);
    } else if (data.eventName === "read") {
      countUpRead(data);
    }
  };

  chatForm.onsubmit = async (e) => {
    e.preventDefault();
    const value = e.target[0].value
    e.target[0].value = "";
    const response = await fetch(e.target.action, {
      method: "POST",
      body: JSON.stringify({ value }),
      headers: {
        'content-type': 'application/json',
      },
    });
  }

  function readChat(chatId) {
    return fetch(`${chatForm.action}/${chatId}`, {
      method: "POST",
      headers: {
        'content-type': 'application/json',
      },
    });
  }

  function appendChat(data) {
    const chatBox = document.createElement("div");
    chatBox.setAttribute("id", "chat-" + data.chatId);
    chatBox.setAttribute("class", "chat-box");

    const isOwn = data.commentBy.username === username;
    if (!isOwn) {
      const chatFace = document.createElement("div");
      chatFace.setAttribute("class", "chat-face");
      const img = document.createElement("img");
      img.setAttribute("src", "/images/" + data.commentBy.icon);
      img.setAttribute("width", 90);
      img.setAttribute("height", 90);
      const username = document.createElement("div");
      username.setAttribute("class", "chat-username");
      username.textContent = data.commentBy.username;
      chatFace.appendChild(img);
      chatFace.appendChild(username);
      chatBox.appendChild(chatFace);
    }

    const chatArea = document.createElement("div");
    chatArea.setAttribute("class", "chat-area");
    const chatComment = document.createElement("div");
    chatComment.setAttribute("class", `chat-comment ${isOwn ? 'my' : 'other'}-chat`);
    chatComment.textContent = data.comment;
    const chatCount = document.createElement("span");
    chatCount.setAttribute("class", "chat-read-count");
    const chatCountIn1 = document.createElement("span");
    chatCountIn1.textContent = "既読: ";
    const chatCountIn2 = document.createElement("span");
    chatCountIn2.textContent = data.count;
    chatCountIn1.appendChild(chatCountIn2);
    chatCount.appendChild(chatCountIn1);
    chatArea.appendChild(chatComment);
    chatArea.appendChild(chatCount);
    chatBox.appendChild(chatArea);
    chatList.appendChild(chatBox);

    scrollBy(0, chatBox.getBoundingClientRect().bottom);
    readChat(data.chatId);
  }

  function countUpRead(data) {
    const chatCount = document.querySelector(`#chat-${data.id} > div > .chat-read-count > span > span`);
    if (!chatCount || chatCount > data.count) {
      return;
    }
    chatCount.textContent = data.count;
  }
}

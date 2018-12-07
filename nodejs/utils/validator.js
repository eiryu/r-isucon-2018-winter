exports.validateUsername = username => {
  if (!username) {
    return "ユーザ名がありません。";
  } else if (username.length < 5 || username.length > 30) {
    return "ユーザ名は5文字以上30文字以下にしてください。";
  } else if (!username.match(/^[a-z0-9_]+$/i)) {
    return "ユーザ名はアルファベットか数字にしてください。";
  }
};

exports.validatePassword = (password, username) => {
  if (!password) {
    return "パスワードがありません。";
  } else if (password.length < 5 || password.length > 30) {
    return "パスワードは5文字以上30文字以下にしてください。";
  } else if (!password.match(/^[a-z0-9_]+$/i)) {
    return "パスワードはアルファベットか数字にしてください。";
  } else if (password.includes(username)) {
    return "パスワードにはユーザ名を含めないでください。";
  }
};

exports.validateGroupname = groupname => {
  if (!groupname) {
    return "グループ名がありません。";
  } else if (groupname.length < 2 || groupname.length > 20) {
    return "グループ名は2文字以上20文字以下にしてください。";
  }
};

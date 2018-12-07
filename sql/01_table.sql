DROP DATABASE IF EXISTS `rine`;
CREATE DATABASE `rine` DEFAULT CHARACTER SET utf8mb4;

USE `rine`;

DROP TABLE IF EXISTS `user`;
CREATE TABLE `user` (
  username varchar(40) NOT NULL,
  salt varchar(40) NOT NULL,
  hash varchar(1000) NOT NULL,
  icon varchar(200) NOT NULL,
  lastname varchar(40) NOT NULL,
  firstname varchar(40) NOT NULL,
  PRIMARY KEY (`username`)
);

DROP TABLE IF EXISTS `groups`;
CREATE TABLE `groups` (
  id int(10) unsigned NOT NULL AUTO_INCREMENT,
  name varchar(40) NOT NULL,
  owner varchar(40) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY (`name`, `owner`)
);

DROP TABLE IF EXISTS `belongs_user_group`;
CREATE TABLE `belongs_user_group` (
  group_id int(10) unsigned NOT NULL,
  username varchar(40) NOT NULL,
  PRIMARY KEY (`group_id`, `username`)
);

DROP TABLE IF EXISTS `chat`;
CREATE TABLE `chat` (
  id int(20) unsigned NOT NULL AUTO_INCREMENT,
  comment varchar(500) NOT NULL,
  comment_by varchar(40) NOT NULL,
  comment_at DATETIME NOT NULL,
  PRIMARY KEY (`id`)
);

DROP TABLE IF EXISTS `belongs_chat_group`;
CREATE TABLE `belongs_chat_group` (
  chat_id int(20) unsigned NOT NULL,
  group_id int(10) unsigned NOT NULL,
  PRIMARY KEY (`chat_id`, `group_id`)
);

DROP TABLE IF EXISTS `read_chat`;
CREATE TABLE `read_chat` (
  chat_id int(20) unsigned NOT NULL,
  username varchar(40) NOT NULL,
  PRIMARY KEY (`chat_id`, `username`)
);

ALTER TABLE belongs_chat_group ADD INDEX belongs_chat_group_group_id_idx(group_id);
ALTER TABLE belongs_user_group ADD INDEX belongs_user_group_username_idx(username);
ALTER TABLE belongs_user_group ADD INDEX belongs_user_group_username_group_id_multi_idx(username, group_id);

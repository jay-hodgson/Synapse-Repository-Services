CREATE TABLE `CHANGES` (
  `CHANGE_NUM` bigint(20) NOT NULL,
  `TIME_STAMP` timestamp NOT NULL,
  `OBJECT_ID` bigint(20) NOT NULL,
  `USER_ID` bigint(20) DEFAULT NULL,
  `PARENT_ID` bigint(20) DEFAULT NULL,
  `OBJECT_TYPE` enum('ENTITY','PRINCIPAL','ACTIVITY','EVALUATION','SUBMISSION','EVALUATION_SUBMISSIONS','FILE','MESSAGE','WIKI','FAVORITE','ACCESS_REQUIREMENT','ACCESS_APPROVAL','TEAM','TABLE','ACCESS_CONTROL_LIST','PROJECT_SETTING','VERIFICATION_SUBMISSION','CERTIFIED_USER_PASSING_RECORD','FORUM','THREAD','REPLY','ENTITY_VIEW') NOT NULL,
  `OBJECT_ETAG` varchar(36) CHARACTER SET latin1 COLLATE latin1_bin DEFAULT NULL,
  `CHANGE_TYPE` enum('CREATE','UPDATE','DELETE') DEFAULT NULL,
  PRIMARY KEY (`OBJECT_ID`,`OBJECT_TYPE`),
  UNIQUE KEY `CHANGE_NUM` (`CHANGE_NUM`)
)

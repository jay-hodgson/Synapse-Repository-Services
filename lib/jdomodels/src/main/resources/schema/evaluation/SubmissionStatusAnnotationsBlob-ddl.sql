CREATE TABLE `SUBSTATUS_ANNOTATIONS_BLOB` (
  `SUBMISSION_ID` bigint(20) NOT NULL,
  VERSION bigint(20) NOT NULL,
  `ANNOTATIONS_BLOB` mediumblob,
  PRIMARY KEY (`SUBMISSION_ID`),
  FOREIGN KEY (`SUBMISSION_ID`) REFERENCES `SUBSTATUS_ANNOTATIONS_OWNER` (`SUBMISSION_ID`) ON DELETE CASCADE
)
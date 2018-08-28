CREATE TABLE IF NOT EXISTS `DOWNLOAD_LIST_ITEM` (
  `PRINCIPAL_ID` bigint(20) NOT NULL,
  `ASSOCIATED_OBJECT_ID` bigint(20) NOT NULL,
  `ASSOCIATED_OBJECT_TYPE` enum ('FileEntity') NOT NULL,
  `FILE_HANDLE_ID` bigint(20) NOT NULL,
  PRIMARY KEY (`PRINCIPAL_ID`, `ASSOCIATED_OBJECT_ID`, `ASSOCIATED_OBJECT_TYPE`, `FILE_HANDLE_ID`),
  CONSTRAINT `DOWNLOAD_ITEM_OWNER_FK` FOREIGN KEY (`PRINCIPAL_ID`) REFERENCES `DOWNLOAD_LIST` (`PRINCIPAL_ID`) ON DELETE CASCADE
)
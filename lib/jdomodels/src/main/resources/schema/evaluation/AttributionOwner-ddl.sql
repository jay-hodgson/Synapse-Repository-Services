CREATE TABLE ATTRIBUTION (
    ID bigint(20) NOT NULL,
    OWNER_ID bigint(20) NOT NULL,
    OWNER_OBJECT_TYPE ENUM('SUBMISSION') NOT NULL,
    ADDED_ON bigint(20) NOT NULL,
    ATTRIBUTION mediumblob,
    PRIMARY KEY (ID)
    /* no FK relationship since this will support other owner object types */
);
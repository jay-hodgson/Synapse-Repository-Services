CREATE TABLE JDOPARTICIPANT (
    USER_ID bigint(20) NOT NULL,
    COMPETITION_ID bigint(20) NOT NULL,
    CREATED_ON bigint(20) NOT NULL,
    PRIMARY KEY (USER_ID, COMPETITION_ID),
    FOREIGN KEY (COMPETITION_ID) REFERENCES JDOCOMPETITION (ID),
    FOREIGN KEY (USER_ID) REFERENCES JDOUSERGROUP (ID)
);
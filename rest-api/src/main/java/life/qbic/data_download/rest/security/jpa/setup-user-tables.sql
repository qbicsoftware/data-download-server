USE data_management;
CREATE TABLE `ngs_measurements`
(
    `measurement_id`  varchar(255) NOT NULL,
    `measurementCode` varchar(255) DEFAULT NULL,
    `projectId`       varchar(255) DEFAULT NULL,
    PRIMARY KEY (`measurement_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;

CREATE TABLE `personal_access_tokens`
(
    `id`                  int(11) NOT NULL AUTO_INCREMENT,
    `tokenValueEncrypted` varchar(255)   DEFAULT NULL,
    `creationDate`        datetime(6) DEFAULT NULL,
    `duration`            decimal(21, 0) DEFAULT NULL,
    `userId`              varchar(255)   DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;

CREATE TABLE `proteomics_measurement`
(
    `measurement_id`  varchar(255) NOT NULL,
    `measurementCode` varchar(255) DEFAULT NULL,
    `projectId`       varchar(255) DEFAULT NULL,
    PRIMARY KEY (`measurement_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;

CREATE TABLE `roles`
(
    `id`          bigint(20) NOT NULL AUTO_INCREMENT,
    `description` varchar(255) DEFAULT NULL,
    `name`        varchar(255) DEFAULT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;

CREATE TABLE `users`
(
    `id`     varchar(255) NOT NULL,
    `active` bit(1)       NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;

CREATE TABLE `user_role`
(
    `id`     bigint(20) NOT NULL AUTO_INCREMENT,
    `roleId` bigint(20) NOT NULL,
    `userId` varchar(255) NOT NULL,
    PRIMARY KEY (`id`),
    KEY      `FK_userId` (`userId`),
    KEY      `FK_roleId` (`roleId`),
    CONSTRAINT `FK_roleId` FOREIGN KEY (`roleId`) REFERENCES `roles` (`id`),
    CONSTRAINT `FK_userId` FOREIGN KEY (`userId`) REFERENCES `users` (`id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_general_ci;

ALTER TABLE `hackwars`.`user`
    ADD COLUMN IF NOT EXISTS `stats_json` MEDIUMTEXT NULL AFTER `stats`,
    ADD COLUMN IF NOT EXISTS `stats_json_version` SMALLINT UNSIGNED NULL AFTER `stats_json`,
    ADD COLUMN IF NOT EXISTS `stats_json_migrated_at` DATETIME NULL AFTER `stats_json_version`;

CREATE TABLE IF NOT EXISTS `hackwars`.`user_stats_text_blob` (
    `blob_id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_num` INT(11) NOT NULL,
    `blob_path` VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `blob_kind` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `text_content` MEDIUMTEXT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`blob_id`),
    UNIQUE KEY `uk_user_stats_text_blob_user_path` (`user_num`, `blob_path`),
    KEY `idx_user_stats_text_blob_user_num` (`user_num`),
    CONSTRAINT `fk_user_stats_text_blob_user_num`
        FOREIGN KEY (`user_num`) REFERENCES `hackwars`.`user` (`num`)
        ON DELETE CASCADE
        ON UPDATE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARSET=latin1;

-- 阶段2：基础数据 - 科室、医生、患者和固定药品字典
-- 对应 Phase 3-4：基础数据模型
-- Flyway 版本区间 V010-V019

-- ============================================================
-- 科室表
-- ============================================================
CREATE TABLE IF NOT EXISTS `department` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(32) NOT NULL COMMENT '科室编码',
    `name` VARCHAR(64) NOT NULL COMMENT '科室名称',
    `parent_id` BIGINT DEFAULT NULL COMMENT '父科室 ID，顶级科室为 NULL',
    `level` INT NOT NULL DEFAULT 1 COMMENT '科室层级：1-一级，2-二级',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序序号',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用，DISABLED-停用',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '科室描述',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_department_code` (`code`),
    INDEX `idx_department_parent_id` (`parent_id`),
    INDEX `idx_department_status` (`status`),
    CONSTRAINT `fk_department_parent` FOREIGN KEY (`parent_id`) REFERENCES `department` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='科室';

-- ============================================================
-- 患者表
-- ============================================================
CREATE TABLE IF NOT EXISTS `patient` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '关联用户账号 ID',
    `name` VARCHAR(64) NOT NULL COMMENT '患者姓名',
    `gender` VARCHAR(16) NOT NULL COMMENT '性别：MALE-男，FEMALE-女',
    `birth_date` DATE NOT NULL COMMENT '出生日期',
    `phone` VARCHAR(20) NOT NULL COMMENT '联系电话',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-活跃，INACTIVE-停用',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_patient_user_id` (`user_id`),
    INDEX `idx_patient_name` (`name`),
    INDEX `idx_patient_phone` (`phone`),
    INDEX `idx_patient_status` (`status`),
    CONSTRAINT `fk_patient_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='患者';

-- 患者扩展档案表
CREATE TABLE IF NOT EXISTS `patient_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `patient_id` BIGINT NOT NULL COMMENT '患者 ID',
    `address` VARCHAR(255) DEFAULT NULL COMMENT '住址',
    `emergency_contact` VARCHAR(64) DEFAULT NULL COMMENT '紧急联系人',
    `emergency_phone` VARCHAR(20) DEFAULT NULL COMMENT '紧急联系电话',
    `allergies` TEXT DEFAULT NULL COMMENT '过敏史',
    `medical_history` TEXT DEFAULT NULL COMMENT '既往史',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_patient_profile_patient_id` (`patient_id`),
    CONSTRAINT `fk_patient_profile_patient` FOREIGN KEY (`patient_id`) REFERENCES `patient` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='患者扩展档案';

-- ============================================================
-- 医生表
-- ============================================================
CREATE TABLE IF NOT EXISTS `doctor` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT NOT NULL COMMENT '关联用户账号 ID',
    `department_id` BIGINT NOT NULL COMMENT '所属科室 ID',
    `name` VARCHAR(64) NOT NULL COMMENT '医生姓名',
    `title` VARCHAR(32) NOT NULL COMMENT '职称：CHIEF-主任医师，DEPUTY_CHIEF-副主任医师，ATTENDING-主治医师，RESIDENT-住院医师',
    `specialty` VARCHAR(255) DEFAULT NULL COMMENT '擅长方向',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用，DISABLED-停用',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doctor_user_id` (`user_id`),
    INDEX `idx_doctor_department_id` (`department_id`),
    INDEX `idx_doctor_name` (`name`),
    INDEX `idx_doctor_status` (`status`),
    CONSTRAINT `fk_doctor_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE RESTRICT,
    CONSTRAINT `fk_doctor_department` FOREIGN KEY (`department_id`) REFERENCES `department` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医生';

-- 医生扩展档案表
CREATE TABLE IF NOT EXISTS `doctor_profile` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `doctor_id` BIGINT NOT NULL COMMENT '医生 ID',
    `education` VARCHAR(64) DEFAULT NULL COMMENT '学历',
    `experience_years` INT DEFAULT NULL COMMENT '从业年限',
    `introduction` TEXT DEFAULT NULL COMMENT '个人简介',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_doctor_profile_doctor_id` (`doctor_id`),
    CONSTRAINT `fk_doctor_profile_doctor` FOREIGN KEY (`doctor_id`) REFERENCES `doctor` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='医生扩展档案';

-- ============================================================
-- 药品字典（固定虚构药品）
-- ============================================================
CREATE TABLE IF NOT EXISTS `drug` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `code` VARCHAR(32) NOT NULL COMMENT '药品编码',
    `name` VARCHAR(128) NOT NULL COMMENT '药品名称',
    `generic_name` VARCHAR(128) DEFAULT NULL COMMENT '通用名',
    `dosage_form` VARCHAR(32) NOT NULL COMMENT '剂型：TABLET-片剂，CAPSULE-胶囊，INJECTION-注射剂，SYRUP-糖浆，OINTMENT-软膏',
    `strength` VARCHAR(32) NOT NULL COMMENT '规格',
    `unit` VARCHAR(16) NOT NULL COMMENT '单位',
    `category` VARCHAR(32) NOT NULL COMMENT '分类：WESTERN-西药，CHINESE-中成药',
    `status` VARCHAR(16) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用，DISABLED-停用',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_drug_code` (`code`),
    INDEX `idx_drug_name` (`name`),
    INDEX `idx_drug_category` (`category`),
    INDEX `idx_drug_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药品字典';

-- 药品成分表
CREATE TABLE IF NOT EXISTS `drug_ingredient` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `drug_id` BIGINT NOT NULL COMMENT '药品 ID',
    `ingredient_name` VARCHAR(128) NOT NULL COMMENT '成分名称',
    `amount` VARCHAR(32) NOT NULL COMMENT '含量',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    INDEX `idx_drug_ingredient_drug_id` (`drug_id`),
    CONSTRAINT `fk_drug_ingredient_drug` FOREIGN KEY (`drug_id`) REFERENCES `drug` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药品成分';

-- 药品相互作用规则表
CREATE TABLE IF NOT EXISTS `drug_interaction_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `drug_a_code` VARCHAR(32) NOT NULL COMMENT '药品 A 编码',
    `drug_b_code` VARCHAR(32) NOT NULL COMMENT '药品 B 编码',
    `severity` VARCHAR(16) NOT NULL COMMENT '严重程度：LOW-低，MEDIUM-中，HIGH-高，CONTRAINDICATED-禁忌',
    `description` VARCHAR(512) NOT NULL COMMENT '相互作用描述',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_drug_interaction_rule` (`drug_a_code`, `drug_b_code`),
    INDEX `idx_drug_interaction_rule_drug_a` (`drug_a_code`),
    INDEX `idx_drug_interaction_rule_drug_b` (`drug_b_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药品相互作用规则';

-- 药品剂量规则表
CREATE TABLE IF NOT EXISTS `drug_dosage_rule` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `drug_code` VARCHAR(32) NOT NULL COMMENT '药品编码',
    `min_dose` DECIMAL(10, 3) NOT NULL COMMENT '最小剂量',
    `max_dose` DECIMAL(10, 3) NOT NULL COMMENT '最大日剂量',
    `max_single_dose` DECIMAL(10, 3) NOT NULL COMMENT '最大单次剂量',
    `frequency` VARCHAR(32) NOT NULL COMMENT '用药频次：QD-每日一次，BID-每日两次，TID-每日三次，QID-每日四次，QN-每晚一次',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    INDEX `idx_drug_dosage_rule_drug_code` (`drug_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药品剂量规则';

-- 药品禁忌表
CREATE TABLE IF NOT EXISTS `drug_contraindication` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `drug_code` VARCHAR(32) NOT NULL COMMENT '药品编码',
    `condition_type` VARCHAR(32) NOT NULL COMMENT '条件类型：ALLERGY-过敏，DISEASE-疾病，PREGNANCY-孕期，AGE-年龄',
    `condition_value` VARCHAR(128) NOT NULL COMMENT '条件值',
    `description` VARCHAR(512) DEFAULT NULL COMMENT '禁忌描述',
    `created_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    INDEX `idx_drug_contraindication_drug_code` (`drug_code`),
    INDEX `idx_drug_contraindication_type` (`condition_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='药品禁忌';

-- ============================================================
-- 初始化演示科室数据
-- ============================================================
INSERT INTO `department` (`code`, `name`, `parent_id`, `level`, `sort_order`, `status`, `description`) VALUES
    ('DEPT_INTERNAL', '内科', NULL, 1, 1, 'ENABLED', '内科系统'),
    ('DEPT_CARDIOLOGY', '心血管内科', 1, 2, 1, 'ENABLED', '心血管疾病诊疗'),
    ('DEPT_NEUROLOGY', '神经内科', 1, 2, 2, 'ENABLED', '神经系统疾病诊疗'),
    ('DEPT_SURGERY', '外科', NULL, 1, 2, 'ENABLED', '外科系统'),
    ('DEPT_GENERAL_SURGERY', '普通外科', 4, 2, 1, 'ENABLED', '普通外科疾病诊疗'),
    ('DEPT_PEDIATRICS', '儿科', NULL, 1, 3, 'ENABLED', '儿童疾病诊疗'),
    ('DEPT_EMERGENCY', '急诊科', NULL, 1, 4, 'ENABLED', '急诊救治');

-- ============================================================
-- 初始化固定虚构药品字典数据
-- ============================================================
INSERT INTO `drug` (`code`, `name`, `generic_name`, `dosage_form`, `strength`, `unit`, `category`, `status`) VALUES
    ('DRG_001', '云脑降压片', '氢氯噻嗪片', 'TABLET', '25mg', '片', 'WESTERN', 'ENABLED'),
    ('DRG_002', '云脑降脂胶囊', '阿托伐他汀钙胶囊', 'CAPSULE', '20mg', '粒', 'WESTERN', 'ENABLED'),
    ('DRG_003', '云脑消炎注射液', '头孢曲松钠注射液', 'INJECTION', '1g', '瓶', 'WESTERN', 'ENABLED'),
    ('DRG_004', '云脑止咳糖浆', '复方甘草口服溶液', 'SYRUP', '100ml', '瓶', 'WESTERN', 'ENABLED'),
    ('DRG_005', '云脑活血软膏', '红花油软膏', 'OINTMENT', '20g', '支', 'CHINESE', 'ENABLED');

-- 药品成分
INSERT INTO `drug_ingredient` (`drug_id`, `ingredient_name`, `amount`) VALUES
    (1, '氢氯噻嗪', '25mg'),
    (2, '阿托伐他汀钙', '20mg'),
    (3, '头孢曲松钠', '1g'),
    (4, '甘草流浸膏', '适量'),
    (5, '红花油', '适量');

-- 药品剂量规则
INSERT INTO `drug_dosage_rule` (`drug_code`, `min_dose`, `max_dose`, `max_single_dose`, `frequency`) VALUES
    ('DRG_001', 0.500, 2.000, 1.000, 'QD'),
    ('DRG_002', 0.500, 1.000, 1.000, 'QN'),
    ('DRG_003', 1.000, 4.000, 2.000, 'BID'),
    ('DRG_004', 5.000, 30.000, 10.000, 'TID'),
    ('DRG_005', 1.000, 3.000, 1.000, 'BID');

-- 药品相互作用规则
INSERT INTO `drug_interaction_rule` (`drug_a_code`, `drug_b_code`, `severity`, `description`) VALUES
    ('DRG_001', 'DRG_003', 'MEDIUM', '利尿剂与头孢类合用可能增加肾毒性风险'),
    ('DRG_002', 'DRG_003', 'LOW', '降脂药与抗生素合用需监测肝功能');

-- 药品禁忌
INSERT INTO `drug_contraindication` (`drug_code`, `condition_type`, `condition_value`, `description`) VALUES
    ('DRG_001', 'ALLERGY', '磺胺类过敏', '对磺胺类药物过敏者禁用'),
    ('DRG_003', 'ALLERGY', '头孢类过敏', '对头孢类药物过敏者禁用'),
    ('DRG_003', 'PREGNANCY', '孕期', '孕妇慎用'),
    ('DRG_004', 'AGE', '婴幼儿', '2岁以下儿童慎用');

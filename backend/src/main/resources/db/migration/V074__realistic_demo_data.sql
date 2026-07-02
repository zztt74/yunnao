-- ============================================================
-- Realistic demo data for course demonstration.
-- This migration keeps historical integration rows for traceability, but hides
-- generated smoke/flow patients from normal ACTIVE lists.
-- ============================================================

SET @doctor_pwd = '$2a$12$piMJdGY0iEeADF.FGSX0puq3R/.FSQEQFZY3FVdfm627F1JEukM6e';
SET @patient_pwd = '$2a$12$Uo9qdpKSV9bFCFagTrk8Oux7vLIEXX6FjvFJ4QAF.H8d99eb0qNS6';

-- Departments needed by demo doctors, schedules and devices.
INSERT INTO `department` (`code`, `name`, `parent_id`, `level`, `sort_order`, `status`, `description`)
VALUES
    ('DEPT_RESPIRATORY', '呼吸与危重症医学科', (SELECT id FROM (SELECT id FROM department WHERE code = 'DEPT_INTERNAL' LIMIT 1) p), 2, 3, 'ENABLED', '呼吸系统常见病、慢阻肺、哮喘及肺部感染诊疗'),
    ('DEPT_RADIOLOGY', '医学影像科', NULL, 1, 8, 'ENABLED', 'DR、CT、MRI、超声等医学影像检查与报告审核')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `parent_id` = VALUES(`parent_id`),
    `level` = VALUES(`level`),
    `sort_order` = VALUES(`sort_order`),
    `status` = VALUES(`status`),
    `description` = VALUES(`description`);

SET @dept_internal = (SELECT id FROM department WHERE code = 'DEPT_INTERNAL' LIMIT 1);
SET @dept_cardiology = (SELECT id FROM department WHERE code = 'DEPT_CARDIOLOGY' LIMIT 1);
SET @dept_respiratory = (SELECT id FROM department WHERE code = 'DEPT_RESPIRATORY' LIMIT 1);
SET @dept_pediatrics = (SELECT id FROM department WHERE code = 'DEPT_PEDIATRICS' LIMIT 1);
SET @dept_surgery = (SELECT id FROM department WHERE code = 'DEPT_GENERAL_SURGERY' LIMIT 1);
SET @dept_emergency = (SELECT id FROM department WHERE code = 'DEPT_EMERGENCY' LIMIT 1);
SET @dept_radiology = (SELECT id FROM department WHERE code = 'DEPT_RADIOLOGY' LIMIT 1);

-- Archive old integration-only doctor accounts and schedules.
UPDATE `doctor` d
JOIN `user_account` u ON u.id = d.user_id
SET d.`status` = 'DISABLED', d.`updated_at` = NOW(3)
WHERE u.`username` IN ('doctor_internal_seed', 'doctor_emergency_seed')
   OR d.`name` LIKE '联调%医生';

UPDATE `user_account`
SET `enabled` = 0, `updated_at` = NOW(3)
WHERE `username` IN ('doctor_internal_seed', 'doctor_emergency_seed');

UPDATE `schedule` s
JOIN `doctor` d ON d.id = s.doctor_id
SET s.`status` = 'CANCELLED',
    s.`cancelled_at` = COALESCE(s.`cancelled_at`, NOW(3)),
    s.`cancel_reason` = COALESCE(s.`cancel_reason`, '历史联调排班归档'),
    s.`updated_at` = NOW(3)
WHERE d.`status` = 'DISABLED'
  AND s.`status` IN ('AVAILABLE', 'FULL');

-- Demo doctor accounts. Password for all demo doctors: DoctorSeed9!2026
INSERT INTO `user_account` (`username`, `real_name`, `phone`, `email`, `password_hash`, `enabled`, `account_non_locked`, `account_non_expired`, `credentials_non_expired`, `must_change_password`, `created_at`, `updated_at`)
VALUES
    ('doctor_chen_mingyuan', '陈明远', '13810012001', 'chen.mingyuan@yunnao.demo', @doctor_pwd, 1, 1, 1, 1, 0, NOW(3), NOW(3)),
    ('doctor_lin_shuxian', '林书贤', '13810012002', 'lin.shuxian@yunnao.demo', @doctor_pwd, 1, 1, 1, 1, 0, NOW(3), NOW(3)),
    ('doctor_zhao_qinglan', '赵清岚', '13810012003', 'zhao.qinglan@yunnao.demo', @doctor_pwd, 1, 1, 1, 1, 0, NOW(3), NOW(3)),
    ('doctor_wang_yiting', '王亦婷', '13810012004', 'wang.yiting@yunnao.demo', @doctor_pwd, 1, 1, 1, 1, 0, NOW(3), NOW(3)),
    ('doctor_hu_jingwei', '胡景维', '13810012005', 'hu.jingwei@yunnao.demo', @doctor_pwd, 1, 1, 1, 1, 0, NOW(3), NOW(3)),
    ('doctor_guo_haoran', '郭浩然', '13810012006', 'guo.haoran@yunnao.demo', @doctor_pwd, 1, 1, 1, 1, 0, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
    `real_name` = VALUES(`real_name`),
    `phone` = VALUES(`phone`),
    `email` = VALUES(`email`),
    `enabled` = 1,
    `account_non_locked` = 1,
    `updated_at` = NOW(3);

INSERT INTO `user_account_role` (`user_id`, `role_id`, `created_at`)
SELECT u.id, r.id, NOW(3)
FROM `user_account` u
JOIN `role` r ON r.`name` = 'DOCTOR'
WHERE u.`username` IN (
    'doctor_chen_mingyuan', 'doctor_lin_shuxian', 'doctor_zhao_qinglan',
    'doctor_wang_yiting', 'doctor_hu_jingwei', 'doctor_guo_haoran'
)
ON DUPLICATE KEY UPDATE `user_id` = VALUES(`user_id`);

INSERT INTO `doctor` (`user_id`, `department_id`, `name`, `title`, `specialty`, `status`, `created_at`, `updated_at`)
SELECT id, @dept_internal, '陈明远', 'ATTENDING', '发热、咳嗽、慢性胃炎、高血压等内科常见病诊疗', 'ENABLED', NOW(3), NOW(3)
FROM `user_account` WHERE `username` = 'doctor_chen_mingyuan'
ON DUPLICATE KEY UPDATE `department_id` = VALUES(`department_id`), `name` = VALUES(`name`), `title` = VALUES(`title`), `specialty` = VALUES(`specialty`), `status` = 'ENABLED', `updated_at` = NOW(3);

INSERT INTO `doctor` (`user_id`, `department_id`, `name`, `title`, `specialty`, `status`, `created_at`, `updated_at`)
SELECT id, @dept_cardiology, '林书贤', 'CHIEF', '冠心病、心绞痛、高血压、心律失常和心血管风险评估', 'ENABLED', NOW(3), NOW(3)
FROM `user_account` WHERE `username` = 'doctor_lin_shuxian'
ON DUPLICATE KEY UPDATE `department_id` = VALUES(`department_id`), `name` = VALUES(`name`), `title` = VALUES(`title`), `specialty` = VALUES(`specialty`), `status` = 'ENABLED', `updated_at` = NOW(3);

INSERT INTO `doctor` (`user_id`, `department_id`, `name`, `title`, `specialty`, `status`, `created_at`, `updated_at`)
SELECT id, @dept_respiratory, '赵清岚', 'DEPUTY_CHIEF', '呼吸道感染、哮喘、慢阻肺、肺部影像随访', 'ENABLED', NOW(3), NOW(3)
FROM `user_account` WHERE `username` = 'doctor_zhao_qinglan'
ON DUPLICATE KEY UPDATE `department_id` = VALUES(`department_id`), `name` = VALUES(`name`), `title` = VALUES(`title`), `specialty` = VALUES(`specialty`), `status` = 'ENABLED', `updated_at` = NOW(3);

INSERT INTO `doctor` (`user_id`, `department_id`, `name`, `title`, `specialty`, `status`, `created_at`, `updated_at`)
SELECT id, @dept_pediatrics, '王亦婷', 'ATTENDING', '儿童发热、咳嗽、腹泻、过敏性疾病及生长发育咨询', 'ENABLED', NOW(3), NOW(3)
FROM `user_account` WHERE `username` = 'doctor_wang_yiting'
ON DUPLICATE KEY UPDATE `department_id` = VALUES(`department_id`), `name` = VALUES(`name`), `title` = VALUES(`title`), `specialty` = VALUES(`specialty`), `status` = 'ENABLED', `updated_at` = NOW(3);

INSERT INTO `doctor` (`user_id`, `department_id`, `name`, `title`, `specialty`, `status`, `created_at`, `updated_at`)
SELECT id, @dept_surgery, '胡景维', 'DEPUTY_CHIEF', '腹痛、体表肿物、创伤清创、术后复查及普通外科评估', 'ENABLED', NOW(3), NOW(3)
FROM `user_account` WHERE `username` = 'doctor_hu_jingwei'
ON DUPLICATE KEY UPDATE `department_id` = VALUES(`department_id`), `name` = VALUES(`name`), `title` = VALUES(`title`), `specialty` = VALUES(`specialty`), `status` = 'ENABLED', `updated_at` = NOW(3);

INSERT INTO `doctor` (`user_id`, `department_id`, `name`, `title`, `specialty`, `status`, `created_at`, `updated_at`)
SELECT id, @dept_emergency, '郭浩然', 'ATTENDING', '胸痛、急腹症、外伤初筛、急诊分级与危重症识别', 'ENABLED', NOW(3), NOW(3)
FROM `user_account` WHERE `username` = 'doctor_guo_haoran'
ON DUPLICATE KEY UPDATE `department_id` = VALUES(`department_id`), `name` = VALUES(`name`), `title` = VALUES(`title`), `specialty` = VALUES(`specialty`), `status` = 'ENABLED', `updated_at` = NOW(3);

INSERT INTO `doctor_profile` (`doctor_id`, `education`, `experience_years`, `introduction`, `created_at`, `updated_at`)
SELECT d.id, '硕士研究生', 10, '长期承担门诊内科首诊和慢病随访工作，重视用药安全、检查指征和患者复诊计划。', NOW(3), NOW(3)
FROM `doctor` d JOIN `user_account` u ON u.id = d.user_id WHERE u.username = 'doctor_chen_mingyuan'
ON DUPLICATE KEY UPDATE `education` = VALUES(`education`), `experience_years` = VALUES(`experience_years`), `introduction` = VALUES(`introduction`), `updated_at` = NOW(3);

INSERT INTO `doctor_profile` (`doctor_id`, `education`, `experience_years`, `introduction`, `created_at`, `updated_at`)
SELECT d.id, '博士研究生', 18, '从事心血管内科临床和教学工作，擅长胸痛风险分层、心血管慢病管理和处方审核。', NOW(3), NOW(3)
FROM `doctor` d JOIN `user_account` u ON u.id = d.user_id WHERE u.username = 'doctor_lin_shuxian'
ON DUPLICATE KEY UPDATE `education` = VALUES(`education`), `experience_years` = VALUES(`experience_years`), `introduction` = VALUES(`introduction`), `updated_at` = NOW(3);

INSERT INTO `doctor_profile` (`doctor_id`, `education`, `experience_years`, `introduction`, `created_at`, `updated_at`)
SELECT d.id, '硕士研究生', 14, '专注呼吸系统常见病与慢病管理，熟悉肺部影像随访、抗感染治疗评估和患者健康教育。', NOW(3), NOW(3)
FROM `doctor` d JOIN `user_account` u ON u.id = d.user_id WHERE u.username = 'doctor_zhao_qinglan'
ON DUPLICATE KEY UPDATE `education` = VALUES(`education`), `experience_years` = VALUES(`experience_years`), `introduction` = VALUES(`introduction`), `updated_at` = NOW(3);

INSERT INTO `doctor_profile` (`doctor_id`, `education`, `experience_years`, `introduction`, `created_at`, `updated_at`)
SELECT d.id, '本科', 9, '负责儿科普通门诊和随访，关注儿童剂量、过敏史、家长用药理解和复诊安排。', NOW(3), NOW(3)
FROM `doctor` d JOIN `user_account` u ON u.id = d.user_id WHERE u.username = 'doctor_wang_yiting'
ON DUPLICATE KEY UPDATE `education` = VALUES(`education`), `experience_years` = VALUES(`experience_years`), `introduction` = VALUES(`introduction`), `updated_at` = NOW(3);

INSERT INTO `doctor_profile` (`doctor_id`, `education`, `experience_years`, `introduction`, `created_at`, `updated_at`)
SELECT d.id, '硕士研究生', 15, '普通外科门诊和日间手术经验丰富，注重术前评估、检查检验闭环和术后恢复指导。', NOW(3), NOW(3)
FROM `doctor` d JOIN `user_account` u ON u.id = d.user_id WHERE u.username = 'doctor_hu_jingwei'
ON DUPLICATE KEY UPDATE `education` = VALUES(`education`), `experience_years` = VALUES(`experience_years`), `introduction` = VALUES(`introduction`), `updated_at` = NOW(3);

INSERT INTO `doctor_profile` (`doctor_id`, `education`, `experience_years`, `introduction`, `created_at`, `updated_at`)
SELECT d.id, '本科', 11, '长期参与急诊分诊和急危重症初筛，擅长识别胸痛、外伤、急腹症等高风险主诉。', NOW(3), NOW(3)
FROM `doctor` d JOIN `user_account` u ON u.id = d.user_id WHERE u.username = 'doctor_guo_haoran'
ON DUPLICATE KEY UPDATE `education` = VALUES(`education`), `experience_years` = VALUES(`experience_years`), `introduction` = VALUES(`introduction`), `updated_at` = NOW(3);

-- Replace old generated patients with five active demo patients.
UPDATE `patient` p
JOIN `user_account` u ON u.id = p.user_id
SET p.`status` = 'INACTIVE', p.`updated_at` = NOW(3)
WHERE u.`username` NOT IN ('weilog', 'patient_wang_jiayi', 'patient_chen_siyuan', 'patient_li_mengqi', 'patient_zhou_yuchen');

UPDATE `user_account` u
LEFT JOIN `patient` p ON p.user_id = u.id
SET u.`enabled` = 0, u.`updated_at` = NOW(3)
WHERE u.`username` LIKE 'smoke_patient_%'
   OR u.`username` LIKE 'flow_patient_%'
   OR u.`username` LIKE 'flow_other_patient_%'
   OR u.`username` LIKE 'appt_probe_%'
   OR u.`username` LIKE 'browser_patient_%'
   OR u.`username` = 'patient_seed'
   OR p.`name` IN ('Smoke Patient', 'Browser Patient', '????')
   OR p.`name` LIKE '联调患者%';

INSERT INTO `user_account` (`username`, `real_name`, `phone`, `email`, `password_hash`, `enabled`, `account_non_locked`, `account_non_expired`, `credentials_non_expired`, `must_change_password`, `created_at`, `updated_at`)
VALUES ('weilog', '刘博雨', '17392152055', 'liu.boyu@example.com', @patient_pwd, 1, 1, 1, 1, 0, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
    `real_name` = '刘博雨',
    `phone` = '17392152055',
    `email` = 'liu.boyu@example.com',
    `enabled` = 1,
    `account_non_locked` = 1,
    `updated_at` = NOW(3);

INSERT INTO `user_account` (`username`, `real_name`, `phone`, `email`, `password_hash`, `enabled`, `account_non_locked`, `account_non_expired`, `credentials_non_expired`, `must_change_password`, `created_at`, `updated_at`)
VALUES
    ('patient_wang_jiayi', '王佳怡', '13910230001', 'wang.jiayi@example.com', @patient_pwd, 1, 1, 1, 1, 0, NOW(3), NOW(3)),
    ('patient_chen_siyuan', '陈思远', '13910230002', 'chen.siyuan@example.com', @patient_pwd, 1, 1, 1, 1, 0, NOW(3), NOW(3)),
    ('patient_li_mengqi', '李梦琪', '13910230003', 'li.mengqi@example.com', @patient_pwd, 1, 1, 1, 1, 0, NOW(3), NOW(3)),
    ('patient_zhou_yuchen', '周予辰', '13910230004', 'zhou.yuchen@example.com', @patient_pwd, 1, 1, 1, 1, 0, NOW(3), NOW(3))
ON DUPLICATE KEY UPDATE
    `real_name` = VALUES(`real_name`),
    `phone` = VALUES(`phone`),
    `email` = VALUES(`email`),
    `password_hash` = VALUES(`password_hash`),
    `enabled` = 1,
    `account_non_locked` = 1,
    `updated_at` = NOW(3);

INSERT INTO `user_account_role` (`user_id`, `role_id`, `created_at`)
SELECT u.id, r.id, NOW(3)
FROM `user_account` u
JOIN `role` r ON r.`name` = 'PATIENT'
WHERE u.`username` IN ('weilog', 'patient_wang_jiayi', 'patient_chen_siyuan', 'patient_li_mengqi', 'patient_zhou_yuchen')
ON DUPLICATE KEY UPDATE `user_id` = VALUES(`user_id`);

INSERT INTO `patient` (`user_id`, `name`, `gender`, `birth_date`, `phone`, `status`, `created_at`, `updated_at`)
SELECT id, '刘博雨', 'MALE', '2002-10-02', '17392152055', 'ACTIVE', NOW(3), NOW(3) FROM `user_account` WHERE `username` = 'weilog'
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `gender` = VALUES(`gender`), `birth_date` = VALUES(`birth_date`), `phone` = VALUES(`phone`), `status` = 'ACTIVE', `updated_at` = NOW(3);

INSERT INTO `patient` (`user_id`, `name`, `gender`, `birth_date`, `phone`, `status`, `created_at`, `updated_at`)
SELECT id, '王佳怡', 'FEMALE', '1994-03-18', '13910230001', 'ACTIVE', NOW(3), NOW(3) FROM `user_account` WHERE `username` = 'patient_wang_jiayi'
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `gender` = VALUES(`gender`), `birth_date` = VALUES(`birth_date`), `phone` = VALUES(`phone`), `status` = 'ACTIVE', `updated_at` = NOW(3);

INSERT INTO `patient` (`user_id`, `name`, `gender`, `birth_date`, `phone`, `status`, `created_at`, `updated_at`)
SELECT id, '陈思远', 'MALE', '1988-11-09', '13910230002', 'ACTIVE', NOW(3), NOW(3) FROM `user_account` WHERE `username` = 'patient_chen_siyuan'
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `gender` = VALUES(`gender`), `birth_date` = VALUES(`birth_date`), `phone` = VALUES(`phone`), `status` = 'ACTIVE', `updated_at` = NOW(3);

INSERT INTO `patient` (`user_id`, `name`, `gender`, `birth_date`, `phone`, `status`, `created_at`, `updated_at`)
SELECT id, '李梦琪', 'FEMALE', '2017-06-22', '13910230003', 'ACTIVE', NOW(3), NOW(3) FROM `user_account` WHERE `username` = 'patient_li_mengqi'
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `gender` = VALUES(`gender`), `birth_date` = VALUES(`birth_date`), `phone` = VALUES(`phone`), `status` = 'ACTIVE', `updated_at` = NOW(3);

INSERT INTO `patient` (`user_id`, `name`, `gender`, `birth_date`, `phone`, `status`, `created_at`, `updated_at`)
SELECT id, '周予辰', 'MALE', '1976-08-15', '13910230004', 'ACTIVE', NOW(3), NOW(3) FROM `user_account` WHERE `username` = 'patient_zhou_yuchen'
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `gender` = VALUES(`gender`), `birth_date` = VALUES(`birth_date`), `phone` = VALUES(`phone`), `status` = 'ACTIVE', `updated_at` = NOW(3);

INSERT INTO `patient_profile` (`patient_id`, `address`, `emergency_contact`, `emergency_phone`, `allergies`, `medical_history`, `created_at`, `updated_at`)
SELECT p.id, '上海市浦东新区张江镇丹桂路 88 号', '刘建平', '13910239901', '无明确药物过敏史', '季节性鼻炎，近期因发热咳嗽咨询分诊', NOW(3), NOW(3)
FROM `patient` p JOIN `user_account` u ON u.id = p.user_id WHERE u.username = 'weilog'
ON DUPLICATE KEY UPDATE `address` = VALUES(`address`), `emergency_contact` = VALUES(`emergency_contact`), `emergency_phone` = VALUES(`emergency_phone`), `allergies` = VALUES(`allergies`), `medical_history` = VALUES(`medical_history`), `updated_at` = NOW(3);

INSERT INTO `patient_profile` (`patient_id`, `address`, `emergency_contact`, `emergency_phone`, `allergies`, `medical_history`, `created_at`, `updated_at`)
SELECT p.id, '北京市朝阳区望京西路 19 号', '王海宁', '13910239902', '青霉素过敏', '高血压病史 3 年，规律随访', NOW(3), NOW(3)
FROM `patient` p JOIN `user_account` u ON u.id = p.user_id WHERE u.username = 'patient_wang_jiayi'
ON DUPLICATE KEY UPDATE `address` = VALUES(`address`), `emergency_contact` = VALUES(`emergency_contact`), `emergency_phone` = VALUES(`emergency_phone`), `allergies` = VALUES(`allergies`), `medical_history` = VALUES(`medical_history`), `updated_at` = NOW(3);

INSERT INTO `patient_profile` (`patient_id`, `address`, `emergency_contact`, `emergency_phone`, `allergies`, `medical_history`, `created_at`, `updated_at`)
SELECT p.id, '杭州市西湖区文三路 156 号', '陈若楠', '13910239903', '无', '2 型糖尿病，口服降糖药治疗中', NOW(3), NOW(3)
FROM `patient` p JOIN `user_account` u ON u.id = p.user_id WHERE u.username = 'patient_chen_siyuan'
ON DUPLICATE KEY UPDATE `address` = VALUES(`address`), `emergency_contact` = VALUES(`emergency_contact`), `emergency_phone` = VALUES(`emergency_phone`), `allergies` = VALUES(`allergies`), `medical_history` = VALUES(`medical_history`), `updated_at` = NOW(3);

INSERT INTO `patient_profile` (`patient_id`, `address`, `emergency_contact`, `emergency_phone`, `allergies`, `medical_history`, `created_at`, `updated_at`)
SELECT p.id, '南京市建邺区河西大街 28 号', '李文静', '13910239904', '头孢类药物皮疹史', '儿童反复呼吸道感染，家属陪诊', NOW(3), NOW(3)
FROM `patient` p JOIN `user_account` u ON u.id = p.user_id WHERE u.username = 'patient_li_mengqi'
ON DUPLICATE KEY UPDATE `address` = VALUES(`address`), `emergency_contact` = VALUES(`emergency_contact`), `emergency_phone` = VALUES(`emergency_phone`), `allergies` = VALUES(`allergies`), `medical_history` = VALUES(`medical_history`), `updated_at` = NOW(3);

INSERT INTO `patient_profile` (`patient_id`, `address`, `emergency_contact`, `emergency_phone`, `allergies`, `medical_history`, `created_at`, `updated_at`)
SELECT p.id, '成都市高新区天府三街 66 号', '周敏', '13910239905', '无', '冠心病支架术后，长期服用抗血小板药物', NOW(3), NOW(3)
FROM `patient` p JOIN `user_account` u ON u.id = p.user_id WHERE u.username = 'patient_zhou_yuchen'
ON DUPLICATE KEY UPDATE `address` = VALUES(`address`), `emergency_contact` = VALUES(`emergency_contact`), `emergency_phone` = VALUES(`emergency_phone`), `allergies` = VALUES(`allergies`), `medical_history` = VALUES(`medical_history`), `updated_at` = NOW(3);

-- Rebuild visible demo schedules for the next seven days.
INSERT INTO `schedule` (`doctor_id`, `department_id`, `schedule_date`, `start_time`, `end_time`, `max_appointments`, `booked_count`, `status`, `created_at`, `updated_at`)
SELECT d.id, d.department_id,
       DATE_ADD(CURRENT_DATE, INTERVAL n.day_offset DAY),
       DATE_ADD(DATE_ADD(CURRENT_DATE, INTERVAL n.day_offset DAY), INTERVAL 8 HOUR) + INTERVAL 30 MINUTE,
       DATE_ADD(DATE_ADD(CURRENT_DATE, INTERVAL n.day_offset DAY), INTERVAL 11 HOUR) + INTERVAL 30 MINUTE,
       12, 0, 'AVAILABLE', NOW(3), NOW(3)
FROM `doctor` d
JOIN `user_account` u ON u.id = d.user_id
JOIN (
    SELECT 1 AS day_offset UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
) n
WHERE u.username IN ('doctor_chen_mingyuan', 'doctor_lin_shuxian', 'doctor_zhao_qinglan', 'doctor_wang_yiting', 'doctor_hu_jingwei', 'doctor_guo_haoran')
UNION ALL
SELECT d.id, d.department_id,
       DATE_ADD(CURRENT_DATE, INTERVAL n.day_offset DAY),
       DATE_ADD(DATE_ADD(CURRENT_DATE, INTERVAL n.day_offset DAY), INTERVAL 14 HOUR),
       DATE_ADD(DATE_ADD(CURRENT_DATE, INTERVAL n.day_offset DAY), INTERVAL 17 HOUR),
       12, 0, 'AVAILABLE', NOW(3), NOW(3)
FROM `doctor` d
JOIN `user_account` u ON u.id = d.user_id
JOIN (
    SELECT 1 AS day_offset UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7
) n
WHERE u.username IN ('doctor_chen_mingyuan', 'doctor_lin_shuxian', 'doctor_zhao_qinglan', 'doctor_wang_yiting', 'doctor_hu_jingwei', 'doctor_guo_haoran');

-- Common drug dictionary. Names are generic-style demo entries, not medication advice.
DELETE di FROM `drug_ingredient` di JOIN `drug` d ON d.id = di.drug_id WHERE d.code LIKE 'DRG_%';
DELETE FROM `drug_dosage_rule` WHERE `drug_code` LIKE 'DRG_%';
DELETE FROM `drug_interaction_rule` WHERE `drug_a_code` LIKE 'DRG_%' OR `drug_b_code` LIKE 'DRG_%';
DELETE FROM `drug_contraindication` WHERE `drug_code` LIKE 'DRG_%';

INSERT INTO `drug` (`code`, `name`, `generic_name`, `dosage_form`, `strength`, `unit`, `category`, `status`)
VALUES
    ('DRG_001', '缬沙坦胶囊', '缬沙坦', 'CAPSULE', '80mg', '粒', 'WESTERN', 'ENABLED'),
    ('DRG_002', '阿托伐他汀钙片', '阿托伐他汀钙', 'TABLET', '20mg', '片', 'WESTERN', 'ENABLED'),
    ('DRG_003', '头孢呋辛酯片', '头孢呋辛酯', 'TABLET', '0.25g', '片', 'WESTERN', 'ENABLED'),
    ('DRG_004', '复方甘草口服溶液', '复方甘草', 'SYRUP', '100ml', '瓶', 'WESTERN', 'ENABLED'),
    ('DRG_005', '云南白药气雾剂', '云南白药', 'OINTMENT', '85g', '瓶', 'CHINESE', 'ENABLED'),
    ('DRG_006', '阿莫西林胶囊', '阿莫西林', 'CAPSULE', '0.25g', '粒', 'WESTERN', 'ENABLED'),
    ('DRG_007', '阿奇霉素片', '阿奇霉素', 'TABLET', '0.25g', '片', 'WESTERN', 'ENABLED'),
    ('DRG_008', '布洛芬混悬液', '布洛芬', 'SYRUP', '100ml:2g', '瓶', 'WESTERN', 'ENABLED'),
    ('DRG_009', '对乙酰氨基酚片', '对乙酰氨基酚', 'TABLET', '0.5g', '片', 'WESTERN', 'ENABLED'),
    ('DRG_010', '奥美拉唑肠溶胶囊', '奥美拉唑', 'CAPSULE', '20mg', '粒', 'WESTERN', 'ENABLED'),
    ('DRG_011', '蒙脱石散', '蒙脱石', 'POWDER', '3g', '袋', 'WESTERN', 'ENABLED'),
    ('DRG_012', '氯雷他定片', '氯雷他定', 'TABLET', '10mg', '片', 'WESTERN', 'ENABLED'),
    ('DRG_013', '硝酸甘油片', '硝酸甘油', 'TABLET', '0.5mg', '片', 'WESTERN', 'ENABLED'),
    ('DRG_014', '阿司匹林肠溶片', '阿司匹林', 'TABLET', '100mg', '片', 'WESTERN', 'ENABLED'),
    ('DRG_015', '盐酸二甲双胍片', '二甲双胍', 'TABLET', '0.5g', '片', 'WESTERN', 'ENABLED'),
    ('DRG_016', '葡萄糖氯化钠注射液', '葡萄糖氯化钠', 'INJECTION', '500ml', '袋', 'WESTERN', 'ENABLED')
ON DUPLICATE KEY UPDATE
    `name` = VALUES(`name`),
    `generic_name` = VALUES(`generic_name`),
    `dosage_form` = VALUES(`dosage_form`),
    `strength` = VALUES(`strength`),
    `unit` = VALUES(`unit`),
    `category` = VALUES(`category`),
    `status` = VALUES(`status`);

INSERT INTO `drug_ingredient` (`drug_id`, `ingredient_name`, `amount`)
SELECT id, `generic_name`, `strength` FROM `drug` WHERE `code` BETWEEN 'DRG_001' AND 'DRG_016';

INSERT INTO `drug_dosage_rule` (`drug_code`, `min_dose`, `max_dose`, `max_single_dose`, `frequency`)
VALUES
    ('DRG_001', 1.000, 1.000, 1.000, 'QD'),
    ('DRG_002', 1.000, 1.000, 1.000, 'QN'),
    ('DRG_003', 1.000, 2.000, 1.000, 'BID'),
    ('DRG_004', 5.000, 10.000, 10.000, 'TID'),
    ('DRG_006', 1.000, 2.000, 1.000, 'TID'),
    ('DRG_007', 1.000, 1.000, 1.000, 'QD'),
    ('DRG_008', 5.000, 10.000, 10.000, 'TID'),
    ('DRG_009', 1.000, 1.000, 1.000, 'Q6H'),
    ('DRG_010', 1.000, 1.000, 1.000, 'QD'),
    ('DRG_011', 1.000, 1.000, 1.000, 'TID'),
    ('DRG_012', 1.000, 1.000, 1.000, 'QD'),
    ('DRG_013', 1.000, 1.000, 1.000, 'PRN'),
    ('DRG_014', 1.000, 1.000, 1.000, 'QD'),
    ('DRG_015', 1.000, 3.000, 1.000, 'BID'),
    ('DRG_016', 1.000, 2.000, 1.000, 'QD');

INSERT INTO `drug_interaction_rule` (`drug_a_code`, `drug_b_code`, `severity`, `description`)
VALUES
    ('DRG_014', 'DRG_008', 'MEDIUM', '阿司匹林与布洛芬同用可能增加胃肠道不良反应风险'),
    ('DRG_014', 'DRG_015', 'LOW', '糖尿病患者长期抗血小板治疗需关注出血风险和胃肠道反应'),
    ('DRG_001', 'DRG_016', 'LOW', '高血压患者补液治疗需结合血压和容量状态评估');

INSERT INTO `drug_contraindication` (`drug_code`, `condition_type`, `condition_value`, `description`)
VALUES
    ('DRG_003', 'ALLERGY', '头孢类过敏', '对头孢类药物过敏者禁用'),
    ('DRG_006', 'ALLERGY', '青霉素过敏', '对青霉素类药物过敏者禁用'),
    ('DRG_008', 'AGE', '婴幼儿', '儿童用药需严格按体重和医嘱调整剂量'),
    ('DRG_014', 'ALLERGY', '阿司匹林过敏', '对阿司匹林或其他水杨酸类过敏者禁用'),
    ('DRG_015', 'RENAL', '严重肾功能不全', '严重肾功能不全患者禁用或需医生评估');

UPDATE `prescription_item` pi
JOIN `drug` d ON d.code = pi.drug_code
SET pi.`drug_name` = d.`name`;

-- Device names and ownership are made demo-realistic while preserving status examples.
UPDATE `device` SET `department_id` = @dept_internal, `name` = '床旁监护仪 MON-2023-01', `location` = '内科门诊观察区 1 号床', `notes` = '门诊观察患者生命体征监测' WHERE `code` = 'DEV-MON-001';
UPDATE `device` SET `department_id` = @dept_internal, `name` = '床旁监护仪 MON-2023-02', `location` = '内科门诊观察区 2 号床', `notes` = '门诊观察患者生命体征监测' WHERE `code` = 'DEV-MON-002';
UPDATE `device` SET `department_id` = @dept_surgery, `name` = '便携式监护仪 MON-2023-03', `location` = '普通外科处置室', `notes` = '清创缝合和术后观察使用' WHERE `code` = 'DEV-MON-003';
UPDATE `device` SET `department_id` = @dept_radiology, `name` = '彩色多普勒超声诊断仪 US-2022-01', `location` = '超声检查室 101', `notes` = '腹部、浅表器官及血管超声检查' WHERE `code` = 'DEV-US-001';
UPDATE `device` SET `department_id` = @dept_surgery, `name` = '便携式超声诊断仪 US-2023-02', `location` = '外科急诊处置室', `notes` = '床旁快速超声评估' WHERE `code` = 'DEV-US-002';
UPDATE `device` SET `department_id` = @dept_radiology, `name` = '64 排螺旋 CT CT-2021-01', `location` = 'CT 检查室 1', `notes` = '胸腹部及头颅 CT 检查' WHERE `code` = 'DEV-CT-001';
UPDATE `device` SET `department_id` = @dept_radiology, `name` = '1.5T 磁共振 MR-2020-01', `location` = 'MRI 检查室 1', `notes` = '神经系统及关节 MRI 检查' WHERE `code` = 'DEV-MRI-001';
UPDATE `device` SET `department_id` = @dept_radiology, `name` = '数字化 X 线摄影系统 DR-2022-01', `location` = 'DR 检查室 1', `notes` = '胸片、四肢及脊柱 X 线检查' WHERE `code` = 'DEV-XRAY-001';
UPDATE `device` SET `department_id` = @dept_internal, `name` = '十二导联心电图机 ECG-2023-01', `location` = '内科心电图室', `notes` = '门诊心电图检查' WHERE `code` = 'DEV-ECG-001';
UPDATE `device` SET `department_id` = @dept_emergency, `name` = '十二导联心电图机 ECG-2023-02', `location` = '急诊抢救室', `notes` = '急诊胸痛患者快速心电图检查' WHERE `code` = 'DEV-ECG-002';
UPDATE `device` SET `department_id` = @dept_internal, `name` = '床旁监护仪 MON-2021-04', `location` = '设备维修暂存区', `notes` = '屏幕亮度异常，已报修待工程师处理' WHERE `code` = 'DEV-MON-004';
UPDATE `device` SET `department_id` = @dept_radiology, `name` = 'CT 扫描仪 CT-2020-02', `location` = 'CT 检查室 2', `notes` = '球管维护中，暂不安排检查' WHERE `code` = 'DEV-CT-002';
UPDATE `device` SET `department_id` = @dept_radiology, `name` = '数字化 X 线摄影系统 DR-2018-02', `location` = '影像科库房', `notes` = '设备老化，已停用待报废流程' WHERE `code` = 'DEV-XRAY-002';

-- Admin display profile.
UPDATE `user_account`
SET `real_name` = COALESCE(`real_name`, '平台管理员'),
    `phone` = COALESCE(`phone`, '13810010000'),
    `email` = COALESCE(`email`, 'admin@yunnao.demo'),
    `updated_at` = NOW(3)
WHERE `username` = 'admin';

-- ============================================================
-- irummate 테스트 데이터 (users 12명 + details + preferences)
-- 실행: psql -h <RDS엔드포인트> -U <사용자> -d irummate -f test-data.sql
-- 주의: lifestyle_vector는 앱의 정규화 공식으로 미리 계산된 값
-- ============================================================

BEGIN;

-- 1. 김민준 (MALE, 컴퓨터공학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_001', 'test001@example.com', '테스트01', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '김민준', '202013278', 24, 'MALE', '컴퓨터공학과' FROM users WHERE oauth_id='kakao_test_001';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 0, '조용히 지내는 걸 좋아해요. 서로 배려하며 지내요!', '{"bedtime": 3, "snoring": 2, "sleepTalking": 2, "organizingStyle": 2, "eatingInRoom": 3, "temperaturePreference": 1, "showerFrequency": 1, "speakerStyle": 3, "callInRoom": 2}'::jsonb, '["BEDTIME", "EATING_IN_ROOM", "CALL_IN_ROOM"]'::jsonb, '[0.5,0.25,0.25,0.25,0.0,0.0,1.0,0.5,1.0]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_001';

-- 2. 이서연 (FEMALE, 경영학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_002', 'test002@example.com', '테스트02', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '이서연', '202488907', 20, 'FEMALE', '경영학과' FROM users WHERE oauth_id='kakao_test_002';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 1, '일찍 자고 일찍 일어나는 편입니다.', '{"bedtime": 1, "snoring": 5, "sleepTalking": 2, "organizingStyle": 5, "eatingInRoom": 2, "temperaturePreference": 1, "showerFrequency": 4, "speakerStyle": 3, "callInRoom": 2}'::jsonb, '["BEDTIME", "SLEEP_TALKING", "SHOWER_FREQUENCY"]'::jsonb, '[0.0,1.0,0.25,1.0,0.0,1.0,1.0,0.5,0.5]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_002';

-- 3. 박지호 (MALE, 전자공학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_003', 'test003@example.com', '테스트03', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '박지호', '202230379', 21, 'MALE', '전자공학과' FROM users WHERE oauth_id='kakao_test_003';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 1, '게임 좋아하지만 이어폰 필수로 써요.', '{"bedtime": 2, "snoring": 3, "sleepTalking": 1, "organizingStyle": 1, "eatingInRoom": 2, "temperaturePreference": 1, "showerFrequency": 3, "speakerStyle": 2, "callInRoom": 3}'::jsonb, '["TEMPERATURE_PREFERENCE", "BEDTIME", "SHOWER_FREQUENCY"]'::jsonb, '[0.25,0.5,0.0,0.0,0.0,0.666667,0.5,1.0,0.5]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_003';

-- 4. 최수아 (FEMALE, 심리학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_004', 'test004@example.com', '테스트04', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '최수아', '202059615', 23, 'FEMALE', '심리학과' FROM users WHERE oauth_id='kakao_test_004';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 1, '깔끔한 성격이라 정리정돈 잘하는 편이에요.', '{"bedtime": 1, "snoring": 5, "sleepTalking": 3, "organizingStyle": 5, "eatingInRoom": 2, "temperaturePreference": 3, "showerFrequency": 2, "speakerStyle": 3, "callInRoom": 1}'::jsonb, '["BEDTIME", "ORGANIZING_STYLE", "SPEAKER_STYLE"]'::jsonb, '[0.0,1.0,0.5,1.0,1.0,0.333333,1.0,0.0,0.5]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_004';

-- 5. 정도윤 (MALE, 기계공학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_005', 'test005@example.com', '테스트05', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '정도윤', '202123238', 19, 'MALE', '기계공학과' FROM users WHERE oauth_id='kakao_test_005';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 0, '운동을 좋아해서 아침에 일찍 나가요.', '{"bedtime": 4, "snoring": 3, "sleepTalking": 4, "organizingStyle": 3, "eatingInRoom": 1, "temperaturePreference": 2, "showerFrequency": 3, "speakerStyle": 1, "callInRoom": 3}'::jsonb, '["TEMPERATURE_PREFERENCE", "SNORING", "EATING_IN_ROOM"]'::jsonb, '[0.75,0.5,0.75,0.5,0.5,0.666667,0.0,1.0,0.0]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_005';

-- 6. 강하은 (FEMALE, 간호학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_006', 'test006@example.com', '테스트06', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '강하은', '202542087', 23, 'FEMALE', '간호학과' FROM users WHERE oauth_id='kakao_test_006';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 0, '시험기간엔 도서관에서 살아요.', '{"bedtime": 2, "snoring": 4, "sleepTalking": 4, "organizingStyle": 3, "eatingInRoom": 3, "temperaturePreference": 3, "showerFrequency": 2, "speakerStyle": 3, "callInRoom": 2}'::jsonb, '["BEDTIME", "ORGANIZING_STYLE", "SPEAKER_STYLE"]'::jsonb, '[0.25,0.75,0.75,0.5,1.0,0.333333,1.0,0.5,1.0]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_006';

-- 7. 조은우 (MALE, 컴퓨터공학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_007', 'test007@example.com', '테스트07', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '조은우', '202262581', 25, 'MALE', '컴퓨터공학과' FROM users WHERE oauth_id='kakao_test_007';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 1, '음악 듣는 걸 좋아하는데 항상 이어폰 씁니다.', '{"bedtime": 3, "snoring": 1, "sleepTalking": 2, "organizingStyle": 5, "eatingInRoom": 3, "temperaturePreference": 2, "showerFrequency": 2, "speakerStyle": 3, "callInRoom": 2}'::jsonb, '["SPEAKER_STYLE", "CALL_IN_ROOM", "SNORING"]'::jsonb, '[0.5,0.0,0.25,1.0,0.5,0.333333,1.0,0.5,1.0]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_007';

-- 8. 윤지우 (FEMALE, 화학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_008', 'test008@example.com', '테스트08', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '윤지우', '202183579', 20, 'FEMALE', '화학과' FROM users WHERE oauth_id='kakao_test_008';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 0, '주말엔 본가에 자주 가요.', '{"bedtime": 5, "snoring": 3, "sleepTalking": 5, "organizingStyle": 4, "eatingInRoom": 3, "temperaturePreference": 2, "showerFrequency": 3, "speakerStyle": 1, "callInRoom": 1}'::jsonb, '["EATING_IN_ROOM", "CALL_IN_ROOM", "BEDTIME"]'::jsonb, '[1.0,0.5,1.0,0.75,0.5,0.666667,0.0,0.0,1.0]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_008';

-- 9. 임시우 (MALE, 경제학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_009', 'test009@example.com', '테스트09', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '임시우', '202030033', 25, 'MALE', '경제학과' FROM users WHERE oauth_id='kakao_test_009';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 0, '야식을 좋아하지만 방에서는 안 먹어요.', '{"bedtime": 2, "snoring": 4, "sleepTalking": 5, "organizingStyle": 1, "eatingInRoom": 2, "temperaturePreference": 2, "showerFrequency": 4, "speakerStyle": 3, "callInRoom": 2}'::jsonb, '["EATING_IN_ROOM", "BEDTIME", "SHOWER_FREQUENCY"]'::jsonb, '[0.25,0.75,1.0,0.0,0.5,1.0,1.0,0.5,0.5]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_009';

-- 10. 한서준 (MALE, 수학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_010', 'test010@example.com', '테스트10', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '한서준', '202444973', 24, 'MALE', '수학과' FROM users WHERE oauth_id='kakao_test_010';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 0, '코골이가 조금 있어요. 미리 말씀드려요!', '{"bedtime": 3, "snoring": 1, "sleepTalking": 3, "organizingStyle": 4, "eatingInRoom": 1, "temperaturePreference": 2, "showerFrequency": 1, "speakerStyle": 3, "callInRoom": 3}'::jsonb, '["TEMPERATURE_PREFERENCE", "SLEEP_TALKING", "EATING_IN_ROOM"]'::jsonb, '[0.5,0.0,0.5,0.75,0.5,0.0,1.0,1.0,0.0]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_010';

-- 11. 오예린 (FEMALE, 영문학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_011', 'test011@example.com', '테스트11', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '오예린', '202549117', 25, 'FEMALE', '영문학과' FROM users WHERE oauth_id='kakao_test_011';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 1, '새벽형 인간입니다. 조용히 다닐게요.', '{"bedtime": 5, "snoring": 5, "sleepTalking": 2, "organizingStyle": 2, "eatingInRoom": 2, "temperaturePreference": 1, "showerFrequency": 1, "speakerStyle": 3, "callInRoom": 2}'::jsonb, '["CALL_IN_ROOM", "BEDTIME", "EATING_IN_ROOM"]'::jsonb, '[1.0,1.0,0.25,0.25,0.0,0.0,1.0,0.5,0.5]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_011';

-- 12. 신재원 (MALE, 물리학과)
INSERT INTO users (oauth_id, email, nickname, profile_image_url, role, status, created_at, updated_at) VALUES ('kakao_test_012', 'test012@example.com', '테스트12', NULL, 'USER', 'ACTIVE', NOW(), NOW());
INSERT INTO user_details (user_id, real_name, student_id, age, gender, department) SELECT id, '신재원', '202241385', 25, 'MALE', '물리학과' FROM users WHERE oauth_id='kakao_test_012';
INSERT INTO user_preferences (user_id, smoking_status, introduce, answers, visible_profile_fields, lifestyle_vector, is_completed, is_matched, created_at, updated_at) SELECT id, 1, '무난하게 잘 지내는 성격입니다.', '{"bedtime": 1, "snoring": 2, "sleepTalking": 5, "organizingStyle": 1, "eatingInRoom": 1, "temperaturePreference": 3, "showerFrequency": 4, "speakerStyle": 1, "callInRoom": 3}'::jsonb, '["SLEEP_TALKING", "EATING_IN_ROOM", "SHOWER_FREQUENCY"]'::jsonb, '[0.0,0.25,1.0,0.0,1.0,1.0,0.0,1.0,0.0]'::vector, true, false, NOW(), NOW() FROM users WHERE oauth_id='kakao_test_012';

COMMIT;

-- 확인용
-- SELECT u.id, u.nickname, d.gender, p.smoking_status, p.lifestyle_vector FROM users u JOIN user_details d ON d.user_id=u.id JOIN user_preferences p ON p.user_id=u.id WHERE u.oauth_id LIKE 'kakao_test_%';

-- 벡터 유사도 테스트: 1번 사용자와 가까운 순 정렬 (같은 성별끼리 매칭한다면 WHERE 추가)
-- SELECT u.nickname, p.lifestyle_vector <=> (SELECT lifestyle_vector FROM user_preferences WHERE user_id=(SELECT id FROM users WHERE oauth_id='kakao_test_001')) AS distance
-- FROM user_preferences p JOIN users u ON u.id=p.user_id WHERE u.oauth_id LIKE 'kakao_test_%' AND u.oauth_id != 'kakao_test_001' ORDER BY distance;

-- 테스트 데이터 삭제 (정리할 때)
-- DELETE FROM match_requests WHERE user_low_id IN (SELECT id FROM users WHERE oauth_id LIKE 'kakao_test_%') OR user_high_id IN (SELECT id FROM users WHERE oauth_id LIKE 'kakao_test_%');
-- DELETE FROM user_preferences WHERE user_id IN (SELECT id FROM users WHERE oauth_id LIKE 'kakao_test_%');
-- DELETE FROM user_details WHERE user_id IN (SELECT id FROM users WHERE oauth_id LIKE 'kakao_test_%');
-- DELETE FROM users WHERE oauth_id LIKE 'kakao_test_%';
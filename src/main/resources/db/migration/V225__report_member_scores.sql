DO $$
BEGIN
    -- 레거시 운영 DB에는 Hibernate가 만든 테이블이 존재하지만, 빈 DB에서는
    -- Flyway가 Hibernate보다 먼저 실행된다. 테이블이 없는 경우에는 이후
    -- ddl-auto:update가 최신 엔티티 정의로 생성하도록 맡긴다.
    IF to_regclass('public.report_member_result') IS NOT NULL THEN
        ALTER TABLE report_member_result
            ADD COLUMN IF NOT EXISTS internal_score NUMERIC(5, 2),
            ADD COLUMN IF NOT EXISTS external_score NUMERIC(5, 2),
            ADD COLUMN IF NOT EXISTS peer_score NUMERIC(5, 2),
            ADD COLUMN IF NOT EXISTS self_feedback_score NUMERIC(5, 2),
            ADD COLUMN IF NOT EXISTS final_score NUMERIC(5, 2),
            ADD COLUMN IF NOT EXISTS external_tool_connected BOOLEAN NOT NULL DEFAULT FALSE,
            ADD COLUMN IF NOT EXISTS reliability_tier VARCHAR(10),
            ADD COLUMN IF NOT EXISTS caution_text TEXT;
    END IF;
END $$;

-- 반드시 애플리케이션 배포 전에 운영 PostgreSQL에서 실행한다.
-- 보존 우선순위: COMPLETED > GENERATING > FAILED. 같은 상태에서는 최신 완료/생성 행을 보존한다.
-- report_member_result 충돌 시 보존 리포트에 이미 있는 결과를 우선하고,
-- 없으면 중복 리포트들 중 result_id가 가장 큰(가장 최근 생성된) 결과 하나를 이관한다.

BEGIN;

LOCK TABLE reports, report_member_result IN ACCESS EXCLUSIVE MODE;

CREATE TEMP TABLE report_dedup_map ON COMMIT DROP AS
WITH ranked AS (
    SELECT
        report_id,
        project_id,
        FIRST_VALUE(report_id) OVER (
            PARTITION BY project_id
            ORDER BY
                CASE status
                    WHEN 'COMPLETED' THEN 0
                    WHEN 'GENERATING' THEN 1
                    WHEN 'FAILED' THEN 2
                    ELSE 3
                END,
                completed_at DESC NULLS LAST,
                created_at DESC NULLS LAST,
                report_id DESC
        ) AS keeper_report_id,
        COUNT(*) OVER (PARTITION BY project_id) AS report_count
    FROM reports
)
SELECT report_id, keeper_report_id
FROM ranked
WHERE report_count > 1;

CREATE TEMP TABLE report_member_result_dedup ON COMMIT DROP AS
SELECT
    result.result_id,
    mapping.keeper_report_id,
    ROW_NUMBER() OVER (
        PARTITION BY mapping.keeper_report_id, result.project_member_id
        ORDER BY
            CASE WHEN result.report_id = mapping.keeper_report_id THEN 0 ELSE 1 END,
            result.result_id DESC
    ) AS result_rank
FROM report_member_result result
JOIN report_dedup_map mapping ON mapping.report_id = result.report_id;

DELETE FROM report_member_result result
USING report_member_result_dedup dedup
WHERE result.result_id = dedup.result_id
  AND dedup.result_rank > 1;

UPDATE report_member_result result
SET report_id = dedup.keeper_report_id
FROM report_member_result_dedup dedup
WHERE result.result_id = dedup.result_id
  AND dedup.result_rank = 1
  AND result.report_id <> dedup.keeper_report_id;

DELETE FROM reports report
USING report_dedup_map mapping
WHERE report.report_id = mapping.report_id
  AND mapping.report_id <> mapping.keeper_report_id;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM reports
        GROUP BY project_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'reports.project_id 중복 정리에 실패했습니다.';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conrelid = 'reports'::regclass
          AND conname = 'uk_report_project'
    ) THEN
        ALTER TABLE reports
            ADD CONSTRAINT uk_report_project UNIQUE (project_id);
    END IF;
END $$;

COMMIT;

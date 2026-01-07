BEGIN;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM bugs WHERE bug_id = 2 AND LOWER(status) = 'in progress') THEN
        RAISE EXCEPTION 'Bug with ID=2 in not in "In Progress" status';
    END IF;
END $$;


UPDATE bugs
SET status = 'Fixed'
WHERE bug_id = 2;

SAVEPOINT add_comment;

INSERT INTO history_corrections(bug_id, user_id, corrected_coment)
VALUES (2, 2, 'Баг виправлено: додано обробку винятків при запуску.');


COMMIT;


SELECT * FROM bugs;
SELECT * FROM history_corrections;

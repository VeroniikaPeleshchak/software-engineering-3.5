BEGIN;

DO $$
BEGIN
	IF NOT EXISTS (SELECT 1 FROM users WHERE user_id = 2 AND LOWER(user_role) = 'developer') THEN 
		RAISE EXCEPTION 'Developer with ID=2 does not exist or is not a Developer';
	END IF;
END $$;

INSERT INTO bugs(software_id, created_user_id, assigned_user_id, bug_name, priority, status, bug_module, description)
VALUES 	(1, 2, 3, 'Некоректне відображення таблиці', 'High', 'New', 'Reports', 
		'При великій кількості записів таблиця не відображається повністю.');

SAVEPOINT fix_module;

UPDATE bugs 
SET bug_module = 'UI'
WHERE bug_name = 'Некоректне відображення таблиці';


ROLLBACK TO fix_module;


SELECT * FROM bugs;
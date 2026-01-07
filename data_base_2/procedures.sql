CREATE OR REPLACE PROCEDURE get_user_bugs_proc (
    IN p_user_id INT
)
LANGUAGE plpgsql
AS $$
DECLARE 
    bug_rec RECORD;
    bug_cur CURSOR FOR
        SELECT b.bug_id,
               b.bug_name,
               b.status,
               b.priority
        FROM bugs b
        WHERE b.created_user_id = p_user_id;
BEGIN
    PERFORM 1 FROM users u WHERE u.user_id = p_user_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'User with id % does not exist', p_user_id;
    END IF;

    OPEN bug_cur;
    LOOP
        FETCH bug_cur INTO bug_rec;
        EXIT WHEN NOT FOUND;
        RAISE NOTICE 'Bug id: %, Name: %, Status: %, Priority: %', 
            bug_rec.bug_id, bug_rec.bug_name, bug_rec.status, bug_rec.priority;
    END LOOP;
    CLOSE bug_cur;

END;
$$;

CALL get_user_bugs_proc(3);



--------------------
CREATE OR REPLACE PROCEDURE assign_bug_proc(
    IN p_bug_id INT,
    IN p_user_id INT
)
LANGUAGE plpgsql
AS $$
DECLARE 
    v_role TEXT;
    v_bug_name TEXT;
    p_result TEXT;
BEGIN
    SELECT user_role INTO STRICT v_role
    FROM users
    WHERE user_id = p_user_id;

    IF LOWER(v_role) <> 'developer' THEN
        RAISE EXCEPTION 'User % is not developer (role: %)', p_user_id, v_role;
    END IF;

    SELECT bug_name INTO STRICT v_bug_name 
    FROM bugs
    WHERE bug_id = p_bug_id;

    UPDATE bugs
    SET assigned_user_id = p_user_id
    WHERE bug_id = p_bug_id;

    p_result := format('Bug "%s" (ID %s) successfully assigned to user %s',
                       v_bug_name, p_bug_id, p_user_id);
    RAISE NOTICE '%', p_result;

EXCEPTION 
    WHEN NO_DATA_FOUND THEN
        p_result := format('User or bug not found (bug_id=%s / user_id=%s)', p_bug_id, p_user_id);
        RAISE NOTICE '%', p_result;
    WHEN OTHERS THEN 
        RAISE EXCEPTION 'Error assigning bug % to user %: %', p_bug_id, p_user_id, SQLERRM;
END;
$$;

CALL assign_bug_proc(3, 2);



--------------------
CREATE OR REPLACE PROCEDURE show_bug_history_proc(
	IN p_bug_id INT
)
LANGUAGE plpgsql
AS $$
DECLARE 
	hist_rec RECORD;
	cur_hist CURSOR FOR
		SELECT 	h.corrected_id, h.date, 
				(u.first_name || ' ' || u.last_name) AS user_name, 
				h.corrected_coment
		FROM history_corrections h
		JOIN users u ON u.user_id = h.user_id
		WHERE h.bug_id = p_bug_id
		ORDER BY h.date;
BEGIN
	PERFORM 1 FROM bugs b WHERE b.bug_id = p_bug_id;
	IF NOT FOUND THEN
		RAISE EXCEPTION 'Bug with id % does not exist', p_bug_id;
	END IF;

	OPEN cur_hist;
	LOOP
		FETCH cur_hist INTO hist_rec;
		EXIT WHEN NOT FOUND;

		CASE 
			WHEN hist_rec.corrected_coment ILIKE '%покращено%' THEN
        		RAISE NOTICE 'Correction ID=% | Date=% | By=% | Comment: %', 
					hist_rec.corrected_id, hist_rec.date, hist_rec.user_name, hist_rec.corrected_coment;
    		ELSE
        		RAISE NOTICE 'Update ID=% | Date=% | By=% | Comment: %',
					hist_rec.corrected_id, hist_rec.date, hist_rec.user_name, hist_rec.corrected_coment;
		END CASE;
	END LOOP;
	CLOSE cur_hist;

EXCEPTION 
	WHEN OTHERS THEN
		RAISE EXCEPTION 'Error getting history for bug %: %', p_bug_id, SQLERRM;
END;
$$;

CALL show_bug_history_proc(3);


--------------------
CALL get_user_bugs_proc(999); 


CALL assign_bug_proc(3, 11);


CALL assign_bug_proc(2, 1);


CALL show_bug_history_proc(25);






































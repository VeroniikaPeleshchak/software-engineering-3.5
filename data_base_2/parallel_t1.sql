--T1
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT bug_id, status FROM bugs WHERE bug_id = 4;

---

SELECT bug_id, status FROM bugs WHERE bug_id = 4;
COMMIT;




--------------------------------------------------
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SELECT bug_id, priority FROM bugs WHERE priority = 'High';

---

SELECT bug_id, priority FROM bugs WHERE priority = 'High';
COMMIT;





--------------------------------------------------
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;
SELECT COUNT(*) FROM bugs WHERE status = 'New';
UPDATE bugs SET status = 'In Progress' WHERE bug_id = 1;

---

COMMIT;





--------------------------------------------------
BEGIN TRANSACTION ISOLATION LEVEL READ COMMITTED;
SELECT bug_id, priority FROM bugs WHERE bug_id = 3; 

---

UPDATE bugs SET priority = 'Low' WHERE bug_id = 3;
COMMIT;



--------------------------------------------------
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SELECT COUNT(*) FROM bugs WHERE priority = 'High'; 

---

UPDATE bugs SET priority = 'Low' WHERE bug_id = 1;    
COMMIT;



--------------------------------------------------
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;
SELECT COUNT(*) FROM bugs WHERE created_user_id = 2; 
INSERT INTO bugs(software_id, created_user_id, assigned_user_id, bug_name, priority, status, bug_module, description)
VALUES (1, 2, 3, 'Bug from T1', 'Medium', 'New', 'Core', 'T1 insert');

---

COMMIT;








DECLARE
    l_id NUMBER;
BEGIN
    -- Validate session using application items
    IF validate_session(
        apex_application.g_x04,  -- Application ID
        apex_application.g_x05   -- Session ID
    ) = 1 THEN
        
        -- Insert new employee record
        INSERT INTO emp (
            ename,
            hiredate,
            sal,
            filename
        ) VALUES (
            apex_application.g_x01,  -- Employee Name
            apex_application.g_x02,  -- Hire Date
            apex_application.g_x03,  -- Salary
            apex_application.g_x06   -- Filename
        )
        RETURNING empno INTO l_id;

        -- Commit the transaction
        COMMIT;

        -- Return success response
        apex_json.open_object;
        apex_json.write('status', 'SUCCESS');
        apex_json.write('id', l_id);
        apex_json.close_object;
        
    ELSE
        -- Return error for invalid session
        apex_json.open_object;
        apex_json.write('status', 'ERROR');
        apex_json.write('message', 'Invalid Request');
        apex_json.close_object;
        
    END IF;
    
EXCEPTION
    WHEN OTHERS THEN
        -- Handle any unexpected errors
        apex_json.open_object;
        apex_json.write('status', 'ERROR');
        apex_json.write('message', SQLERRM);
        apex_json.close_object;
END;

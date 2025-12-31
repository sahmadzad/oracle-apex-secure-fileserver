CREATE OR REPLACE FUNCTION validate_session(
    p_app_id     IN NUMBER,
    p_empno      IN NUMBER,
    p_app_cs     IN VARCHAR2,
    p_session_id IN NUMBER
) RETURN NUMBER
IS
    -- ============================================
    -- CONSTANTS
    -- ============================================
    C_SESSION_VALID    CONSTANT NUMBER := 1;
    C_SESSION_INVALID  CONSTANT NUMBER := 0;
    
    -- ============================================
    -- VARIABLES
    -- ============================================
    v_is_valid         NUMBER;
    v_session_count    NUMBER;
    v_cs_check_result  NUMBER;
    
BEGIN
    -- ============================================
    -- LOG SESSION VALIDATION ATTEMPT
    -- ============================================
    DBMS_APPLICATION_INFO.SET_MODULE(
        module_name => 'VALIDATE_SESSION',
        action_name => 'CHECKING'
    );
    
    -- ============================================
    -- VALIDATE CS TOKEN (Cross-Site Request Forgery)
    -- ============================================
    IF p_app_cs IS NULL THEN
        -- NULL CS token is allowed for backward compatibility
        v_cs_check_result := C_SESSION_VALID;
    ELSE
        -- Validate CS token if provided
        v_cs_check_result := check_cs(p_empno, p_session_id, p_app_cs);
    END IF;
    
    -- ============================================
    -- EARLY EXIT IF CS VALIDATION FAILS
    -- ============================================
    IF v_cs_check_result != C_SESSION_VALID THEN
        RETURN C_SESSION_INVALID;
    END IF;
    
    -- ============================================
    -- CHECK APEX SESSION VALIDITY
    -- ============================================
    SELECT COUNT(*)
    INTO v_session_count
    FROM APEX_WORKSPACE_SESSIONS s
    WHERE s.apex_session_id = p_session_id
      AND s.session_life_timeout_on > SYSTIMESTAMP
      AND s.session_idle_timeout_on > SYSTIMESTAMP
      -- Optional: Uncomment for workspace-specific validation
      -- AND s.workspace_id = p_app_id
      AND ROWNUM = 1; -- Optimize performance
    
    -- ============================================
    -- DETERMINE SESSION VALIDITY
    -- ============================================
    IF v_session_count > 0 THEN
        v_is_valid := C_SESSION_VALID;
    ELSE
        v_is_valid := C_SESSION_INVALID;
    END IF;
    
    -- ============================================
    -- LOG RESULT (Optional - for debugging)
    -- ============================================
    DBMS_APPLICATION_INFO.SET_MODULE(
        module_name => 'VALIDATE_SESSION',
        action_name => CASE 
                        WHEN v_is_valid = C_SESSION_VALID 
                        THEN 'VALID' 
                        ELSE 'INVALID' 
                       END
    );
    
    RETURN v_is_valid;
    
EXCEPTION
    -- ============================================
    -- ERROR HANDLING
    -- ============================================
    WHEN NO_DATA_FOUND THEN
        -- Session not found
        RETURN C_SESSION_INVALID;
        
    WHEN TOO_MANY_ROWS THEN
        -- Multiple sessions found (should not happen with apex_session_id)
        -- Log error and return invalid
        DBMS_APPLICATION_INFO.SET_MODULE(
            module_name => 'VALIDATE_SESSION',
            action_name => 'ERROR_MULTIPLE_SESSIONS'
        );
        RETURN C_SESSION_INVALID;
        
    WHEN OTHERS THEN
        -- Log unexpected error and return invalid
        DBMS_APPLICATION_INFO.SET_MODULE(
            module_name => 'VALIDATE_SESSION',
            action_name => 'ERROR_UNEXPECTED'
        );
        
        -- Consider logging to error table for production
        -- INSERT INTO session_validation_errors(...) VALUES(...);
        
        RETURN C_SESSION_INVALID;
END validate_session;
/

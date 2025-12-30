DECLARE
    l_empno     VARCHAR2(50);
    l_filename  VARCHAR2(200);
    l_response  CLOB;
    l_file      VARCHAR2(4000);
    l_url       VARCHAR2(4000);

    c_fileserver CONSTANT VARCHAR2(200) :=
        'http://file_server_ip_address:port/k/';
BEGIN
    l_empno := apex_application.g_x01;

    apex_debug.message('EMP_NO=' || l_empno);

    BEGIN
        SELECT filename
        INTO   l_filename
        FROM   emp
        WHERE  empno = l_empno;

        apex_debug.message('FILENAME=' || l_filename);
    EXCEPTION
        WHEN NO_DATA_FOUND THEN
            apex_json.open_object;
            apex_json.write('error','EMP not found');
            apex_json.close_object;
            return;
    END;

    apex_debug.message('Calling Java REST');

    apex_web_service.g_request_headers.delete;

    apex_web_service.g_request_headers(1).name  := 'X-Doc-Id';
    apex_web_service.g_request_headers(1).value := l_empno;

    apex_web_service.g_request_headers(2).name  := 'X-File-Name';
    apex_web_service.g_request_headers(2).value := l_filename;

    apex_web_service.g_request_headers(3).name  := 'X-Doc-Type';
    apex_web_service.g_request_headers(3).value := 'EMP_DOC';

    apex_web_service.g_request_headers(4).name  := 'p_app_id';
    apex_web_service.g_request_headers(4).value := :APP_ID;

    apex_web_service.g_request_headers(5).name  := 'p_session_id';
    apex_web_service.g_request_headers(5).value := :APP_SESSION;

    l_response := apex_web_service.make_rest_request(
        p_url         => 'http://weblogic_server_ip_adrress:port/rest_token/AccessToDocumentV2',
        p_http_method => 'POST'
    );

    apex_debug.message('RAW RESPONSE=' || substr(l_response,1,4000));

    IF substr(l_response,1,1) <> '{' THEN
        apex_json.open_object;
        apex_json.write('error','Java returned invalid JSON');
        apex_json.write('raw',substr(l_response,1,2000));
        apex_json.close_object;
        return;
    END IF;

    BEGIN
        l_file := json_value(l_response,'$.file');
        apex_debug.message('TEMP FILE=' || l_file);
    EXCEPTION
        WHEN OTHERS THEN
            apex_json.open_object;
            apex_json.write('error','JSON parse failed');
            apex_json.write('raw',substr(l_response,1,2000));
            apex_json.close_object;
            return;
    END;

    l_url := c_fileserver || l_file;
    apex_debug.message('PUBLIC URL=' || l_url);

    apex_json.open_object;
    apex_json.write('url',l_url);
    apex_json.close_object;

EXCEPTION
    WHEN OTHERS THEN
        apex_json.open_object;
        apex_json.write('error','APEX crash');
        apex_json.write('sqlcode',SQLCODE);
        apex_json.write('sqlerrm',SQLERRM);
        apex_json.write('stack',DBMS_UTILITY.format_error_backtrace);
        apex_json.close_object;

END;	

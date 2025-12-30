select EMPNO,
    ENAME,
    JOB,
    to_char(HIREDATE,'YYYY/MM/DD') HIREDATE,
    SAL,
        '<a href="javascript:void(0);" id="link_' || EMPNO || '">
        <img src="#IMAGE_PREFIX#app_ui/img/icons/apex-edit-view.png" class="apex-edit-view"
        tag="DOWNLOAD_IMG" data-code="' || EMPNO || '"></a>' IMG


from emp

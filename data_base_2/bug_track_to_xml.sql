SELECT 
    xmlroot(
        xmlelement(
            NAME "bug_tracking_system",
            xmlagg(
                xmlelement(
                    NAME "software",
                    xmlattributes(
                        s.software_id AS "id"
                    ),
                    xmlelement(NAME "name", s.software_name),
                    xmlelement(NAME "version", s.software_version),
                    xmlelement(NAME "category", s.category),
                    (
                        SELECT xmlagg(
                            xmlelement(
                                NAME "bug",
                                xmlattributes(
                                    b.bug_id AS "id",
                                    b.date AS "date"
                                ),
                                xmlelement(NAME "name", b.bug_name),
                                xmlelement(NAME "priority", b.priority),
                                xmlelement(NAME "status", b.status),
                                xmlelement(NAME "module", b.bug_module),
                                xmlelement(NAME "description", b.description),
                                xmlelement(
                                    NAME "created_by",
                                    xmlforest(
                                        u1.first_name AS "first_name",
                                        u1.last_name AS "last_name",
                                        u1.email AS "email",
                                        u1.user_role AS "role"
                                    )
                                ),
                                xmlelement(
                                    NAME "assigned_to",
                                    xmlforest(
                                        u2.first_name AS "first_name",
                                        u2.last_name AS "last_name",
                                        u2.email AS "email",
                                        u2.user_role AS "role"
                                    )
                                ),
                                xmlelement(
                                    NAME "history_corrections",
                                    (
                                        SELECT xmlagg(
                                            xmlelement(
                                                NAME "correction",
                                                xmlattributes(
                                                    hc.corrected_id AS "id",
                                                    hc.date AS "date"
                                                ),
                                                xmlelement(NAME "comment", hc.corrected_coment),
                                                xmlelement(
                                                    NAME "corrected_by",
                                                    xmlforest(
                                                        u3.first_name AS "first_name",
                                                        u3.last_name AS "last_name",
                                                        u3.email AS "email"
                                                    )
                                                )
                                            )
                                        )
                                        FROM history_corrections hc
                                        JOIN users u3 ON u3.user_id = hc.user_id
                                        WHERE hc.bug_id = b.bug_id
                                    )
                                )
                            )
                        )
                        FROM bugs b
                        JOIN users u1 ON u1.user_id = b.created_user_id
                        JOIN users u2 ON u2.user_id = b.assigned_user_id
                        WHERE b.software_id = s.software_id
                    )
                )
            )
        ),
        VERSION '1.0'
    ) AS bug_tracking_xml
FROM softwares s;





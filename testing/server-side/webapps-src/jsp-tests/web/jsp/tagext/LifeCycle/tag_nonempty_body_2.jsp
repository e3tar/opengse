<%--
 Test for translation-time verification
 Verify that single and double quotes can be used.
 Verify that a non-empty body can be passed 
 The body will be ignored
--%>
<%@ taglib uri="/TestLib.tld" prefix="x" %>

<x:silly a='3' b="2" c="5">
HI!
</x:silly>
tag_nonempty_body_2 test PASSED

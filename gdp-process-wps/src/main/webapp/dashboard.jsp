<%@ page contentType="text/html;charset=UTF-8" %>
<% response.setHeader("Pragma", "no-cache");%>
<% response.setHeader("Cache-Control", "no-store");%>
<% response.setDateHeader("Expires", -1);%>

<!DOCTYPE html>
<html>
    <head>
        <link rel="stylesheet" type="text/css" href="css/dashboard.css" />
        <jsp:include page="scripts.jsp" />
        <script src="js/dashboard.js"></script>
        <title>GDP Web Processing Service Dashboard</title>
    </head>
    <body>
        <p>Geo Data Portal Web Processing Service Dashboard</p>
        <input id="loadProcessesButton" type="button" value="Load Processes" />Last loaded: <span id="lastProcessLoad">never</span>
        <br />
        <input id="previousPageButton" type="button" value="&lt; Previous Page"/>
        <input id="nextPageButton" type="button" value="Next Page &gt;"/>

        <div id="processData">Load processes...</div>
    </body>
</html>
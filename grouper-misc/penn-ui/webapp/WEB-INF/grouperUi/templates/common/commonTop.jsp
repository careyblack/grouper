<%@ include file="../common/commonTaglib.jsp" %>
<div id="topLeftLogo">
  <img src="../public/assets/logo.gif" id="topLeftLogoImage" />
</div>
<div id="topRightLogo">
  <img src="../public/assets/grouper.gif" id="topRightLogoImage" />
</div>
<div id="navbar"> 
     ${guiSettings.text.screenWelcome} ${guiSettings.loggedInSubject.subject.description} 
     &nbsp; &nbsp;  
     <a href="#" onclick="return allObjects.pageHelpers.logout();"><img src="../public/assets/logout.gif" border="0" id="logoutImage" alt="Log out" /></a>
     <a href="#" onclick="return allObjects.pageHelpers.logout();">Log out</a>
</div>


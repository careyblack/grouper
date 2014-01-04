package edu.internet2.middleware.grouper.grouperUi.serviceLogic;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import edu.internet2.middleware.grouper.Field;
import edu.internet2.middleware.grouper.FieldFinder;
import edu.internet2.middleware.grouper.GrouperSession;
import edu.internet2.middleware.grouper.Member;
import edu.internet2.middleware.grouper.MemberFinder;
import edu.internet2.middleware.grouper.Stem;
import edu.internet2.middleware.grouper.StemCopy;
import edu.internet2.middleware.grouper.StemFinder;
import edu.internet2.middleware.grouper.StemMove;
import edu.internet2.middleware.grouper.Stem.Scope;
import edu.internet2.middleware.grouper.exception.InsufficientPrivilegeException;
import edu.internet2.middleware.grouper.exception.StemDeleteException;
import edu.internet2.middleware.grouper.grouperUi.beans.api.GuiObjectBase;
import edu.internet2.middleware.grouper.grouperUi.beans.api.GuiStem;
import edu.internet2.middleware.grouper.grouperUi.beans.dojo.DojoComboLogic;
import edu.internet2.middleware.grouper.grouperUi.beans.dojo.DojoComboQueryLogicBase;
import edu.internet2.middleware.grouper.grouperUi.beans.json.GuiResponseJs;
import edu.internet2.middleware.grouper.grouperUi.beans.json.GuiScreenAction;
import edu.internet2.middleware.grouper.grouperUi.beans.json.GuiScreenAction.GuiMessageType;
import edu.internet2.middleware.grouper.grouperUi.beans.ui.GrouperRequestContainer;
import edu.internet2.middleware.grouper.grouperUi.beans.ui.StemContainer;
import edu.internet2.middleware.grouper.grouperUi.beans.ui.TextContainer;
import edu.internet2.middleware.grouper.internal.dao.QueryOptions;
import edu.internet2.middleware.grouper.membership.MembershipType;
import edu.internet2.middleware.grouper.misc.GrouperObject;
import edu.internet2.middleware.grouper.misc.GrouperObjectFinder;
import edu.internet2.middleware.grouper.misc.GrouperObjectFinder.ObjectPrivilege;
import edu.internet2.middleware.grouper.privs.NamingPrivilege;
import edu.internet2.middleware.grouper.privs.Privilege;
import edu.internet2.middleware.grouper.subj.SubjectHelper;
import edu.internet2.middleware.grouper.ui.GrouperUiFilter;
import edu.internet2.middleware.grouper.ui.util.GrouperUiConfig;
import edu.internet2.middleware.grouper.ui.util.GrouperUiUserData;
import edu.internet2.middleware.grouper.ui.util.GrouperUiUtils;
import edu.internet2.middleware.grouper.userData.GrouperUserDataApi;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Subject;

/**
 * operations in the stem screen
 * @author mchyzer
 *
 */
public class UiV2Stem {

  /** logger */
  protected static final Log LOG = LogFactory.getLog(UiV2Stem.class);

  /**
   * submit button on parent folder search model dialog
   * @param request
   * @param response
   */
  public void stemSearchFormSubmit(final HttpServletRequest request, HttpServletResponse response) {
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
    
    GrouperSession grouperSession = null;
    
    try {
      grouperSession = GrouperSession.start(loggedInSubject);
  
  
      stemSearchFormSubmitHelper(request, response, StemSearchType.createFolder);
      
    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
    
  }

  /**
   * submit button on parent folder search model dialog for create groups
   * @param request
   * @param response
   */
  public void stemSearchGroupFormSubmit(final HttpServletRequest request, HttpServletResponse response) {
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
    
    GrouperSession grouperSession = null;
    
    try {
      grouperSession = GrouperSession.start(loggedInSubject);
  
  
      stemSearchFormSubmitHelper(request, response, StemSearchType.createGroup);
      
    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
    
  }

  
  
  /**
   * submit button on parent folder search model dialog
   * @param request
   * @param response
   * @param stemSearchType
   */
  private void stemSearchFormSubmitHelper(final HttpServletRequest request, HttpServletResponse response, 
      StemSearchType stemSearchType) {
   
    GrouperSession grouperSession = null;

    GrouperRequestContainer grouperRequestContainer = GrouperRequestContainer.retrieveFromRequestOrCreate();
    
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
    GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();
     
    try {
  
      grouperSession = GrouperSession.start(loggedInSubject);
  
      StemContainer stemContainer = grouperRequestContainer.getStemContainer();

      stemContainer.setStemSearchType(stemSearchType);

      String searchString = request.getParameter("stemSearch");
      
      boolean searchOk = GrouperUiUtils.searchStringValid(searchString);
      if (!searchOk) {
        
        guiResponseJs.addAction(GuiScreenAction.newInnerHtml("#folderSearchResultsId", 
            TextContainer.retrieveFromRequest().getText().get("stemSearchNotEnoughChars")));
        return;
      }
      
      stemContainer.setParentStemFilterText(searchString);
      
      String pageSizeString = request.getParameter("pagingTagPageSize");
      int pageSize = -1;
      if (!StringUtils.isBlank(pageSizeString)) {
        pageSize = GrouperUtil.intValue(pageSizeString);
      } else {
        pageSize = GrouperUiConfig.retrieveConfig().propertyValueInt("pager.pagesize.default", 50);
      }
      stemContainer.getParentStemGuiPaging().setPageSize(pageSize);
      
      //1 indexed
      String pageNumberString = request.getParameter("pagingTagPageNumber");
      
      int pageNumber = 1;
      if (!StringUtils.isBlank(pageNumberString)) {
        pageNumber = GrouperUtil.intValue(pageNumberString);
      }
      
      stemContainer.getParentStemGuiPaging().setPageNumber(pageNumber);

      QueryOptions queryOptions = new QueryOptions();
      queryOptions.paging(pageSize, pageNumber, true);
      
      StemFinder stemFinder = new StemFinder().assignScope(searchString).assignSplitScope(true)
          .addPrivilege(NamingPrivilege.STEM).assignSubject(loggedInSubject)
          .assignQueryOptions(queryOptions);
      
      //set of stems that match, and what memberships each subject has
      Set<Stem> results = stemFinder.findStems();

      Set<GuiStem> guiResults = GuiStem.convertFromStems(results);
      
      stemContainer.setParentStemSearchResults(guiResults);
      
      stemContainer.getParentStemGuiPaging().setTotalRecordCount(queryOptions.getQueryPaging().getTotalRecordCount());

      if (GrouperUtil.length(guiResults) == 0) {
   
        guiResponseJs.addAction(GuiScreenAction.newInnerHtml("#folderSearchResultsId", 
            TextContainer.retrieveFromRequest().getText().get("stemSearchNoStemsFound")));
        return;
      }
            
      guiResponseJs.addAction(GuiScreenAction.newInnerHtmlFromJsp("#folderSearchResultsId", 
          "/WEB-INF/grouperUi2/stem/parentFolderSearchResults.jsp"));
      
    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
  }
  
  /**
   * type of stem search
   */
  public static enum StemSearchType {
    
    /**
     * folder that can create a group
     */
    createGroup("stemSearchDescriptionNewGroups", "stemSearchGroupFormSubmit"),
    
    /**
     * folder that can create a folder
     */
    createFolder("stemSearchDescriptionNewFolders", "stemSearchFormSubmit");

    /**
     * construct with description
     * @param theKeyDescription
     */
    private StemSearchType(String theKeyDescription, String theOperationMethod) {
      this.keyDescription = theKeyDescription;
      this.operationMethod = theOperationMethod;
    }
    
    /**
     * stemSearchFormSubmit or stemSearchGroupFormSubmit etc
     */
    private String operationMethod;
    
    /**
     * stemSearchFormSubmit or stemSearchGroupFormSubmit etc
     * @return the method name
     */
    public String getOperationMethod() {
      return this.operationMethod;
    }

    /**
     * stemSearchFormSubmit or stemSearchGroupFormSubmit etc
     * @param operationMethod1
     */
    public void setOperationMethod(String operationMethod1) {
      this.operationMethod = operationMethod1;
    }

    /**
     * key for search screen description in the externalized text file
     */
    private String keyDescription;
    
    /**
     * key for search screen description in the externalized text file
     * @param theKeyDescription
     */
    public void setKeyDescription(String theKeyDescription) {
      this.keyDescription = theKeyDescription;
    }
    
    /**
     * key for search screen description in the externalized text file
     * @return description
     */
    public String getKeyDescription() {
      return this.keyDescription;
    }
    
  }
  
  /**
   * combo filter copy parent folder
   * @param request
   * @param response
   */
  public void stemCopyParentFolderFilter(final HttpServletRequest request, HttpServletResponse response) {

    //run the combo logic
    DojoComboLogic.logic(request, response, new DojoComboQueryLogicBase<Stem>() {

      /**
       * 
       */
      @Override
      public Stem lookup(HttpServletRequest request, GrouperSession grouperSession, String query) {
        Subject loggedInSubject = grouperSession.getSubject();
        Stem theStem = new StemFinder().addPrivilege(NamingPrivilege.STEM).assignSubject(loggedInSubject)
            .assignFindByUuidOrName(true).assignScope(query).findStem();
        return theStem;
      }

      /**
       * 
       */
      @Override
      public Collection<Stem> search(HttpServletRequest request, GrouperSession grouperSession, String query) {
        Subject loggedInSubject = grouperSession.getSubject();
        int stemComboSize = GrouperUiConfig.retrieveConfig().propertyValueInt("uiV2.stemComboboxResultSize", 200);
        QueryOptions queryOptions = QueryOptions.create(null, null, 1, stemComboSize);
        return new StemFinder().addPrivilege(NamingPrivilege.STEM).assignScope(query).assignSubject(loggedInSubject)
            .assignSplitScope(true).assignQueryOptions(queryOptions).findStems();
      }

      /**
       * 
       * @param t
       * @return
       */
      @Override
      public String retrieveId(GrouperSession grouperSession, Stem t) {
        return t.getId();
      }
      
      /**
       * 
       */
      @Override
      public String retrieveLabel(GrouperSession grouperSession, Stem t) {
        return t.getDisplayName();
      }

      /**
       * 
       */
      @Override
      public String retrieveHtmlLabel(GrouperSession grouperSession, Stem t) {
        //description could be null?
        String label = GrouperUiUtils.escapeHtml(t.getDisplayName(), true);
        String htmlLabel = "<img src=\"../../grouperExternal/public/assets/images/folder.gif\" /> " + label;
        return htmlLabel;
      }

      /**
       * 
       */
      @Override
      public String initialValidationError(HttpServletRequest request, GrouperSession grouperSession) {
        Stem stem = retrieveStemHelper(request, true).getStem();
        
        if (stem == null) {
          return TextContainer.retrieveFromRequest().getText().get("stemCopyInsufficientPrivileges");
        }
        return null;
      }
    });
    
  }

  
  /**
   * 
   * @param request
   * @param response
   */
  public void stemCopySubmit(HttpServletRequest request, HttpServletResponse response) {

    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();

    GrouperSession grouperSession = null;

    Stem stem = null;

    try {

      grouperSession = GrouperSession.start(loggedInSubject);

      stem = retrieveStemHelper(request, true).getStem();
    
      if (stem == null) {
        return;
      }

      GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();

      String displayExtension = request.getParameter("displayExtension");
      String extension = request.getParameter("extension");

      boolean copyGroupAttributes = GrouperUtil.booleanValue(request.getParameter("copyGroupAttributes[]"), false);
      boolean copyListMemberships = GrouperUtil.booleanValue(request.getParameter("copyListMemberships[]"), false);
      boolean copyGroupPrivileges = GrouperUtil.booleanValue(request.getParameter("copyGroupPrivileges[]"), false);
      boolean copyListMembershipsInOtherGroups = GrouperUtil.booleanValue(request.getParameter("copyListMembershipsInOtherGroups[]"), false);
      boolean copyPrivsInOtherGroups = GrouperUtil.booleanValue(request.getParameter("copyPrivsInOtherGroups[]"), false);
      boolean copyFolderPrivs = GrouperUtil.booleanValue(request.getParameter("copyFolderPrivs[]"), false);
      
      String parentFolderId = request.getParameter("parentFolderComboName");
      
      //just get what they typed in
      if (StringUtils.isBlank(parentFolderId)) {
        parentFolderId = request.getParameter("parentFolderComboNameDisplay");
      }
      
      final Stem parentFolder = StringUtils.isBlank(parentFolderId) ? null : new StemFinder().addPrivilege(NamingPrivilege.STEM)
          .assignSubject(loggedInSubject)
          .assignScope(parentFolderId).assignFindByUuidOrName(true).findStem();
      
      if (parentFolder == null) {
        
        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
            TextContainer.retrieveFromRequest().getText().get("stemCopyCantFindParentStemId")));

        return;
        
      }

      //MCH 20131224: dont need this since we are searching by stemmed folders above
      //{
      //  //make sure the user can stem the parent folder
      //  boolean canStemParent = (Boolean)GrouperSession.callbackGrouperSession(
      //      GrouperSession.staticGrouperSession().internal_getRootSession(), new GrouperSessionHandler() {
      //        
      //        @Override
      //        public Object callback(GrouperSession grouperSession) throws GrouperSessionException {
      //          return parentFolder.hasStem(loggedInSubject);
      //        }
      //      });
      //
      //  if (!canStemParent) {
      //
      //    guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
      //        TextContainer.retrieveFromRequest().getText().get("stemCopyCantStemParent")));
      //    return;
      //
      //  }
      //}
      
      Stem newStem = null;
      
      try {

        //get the new folder that was created
        newStem = new StemCopy(stem, parentFolder).copyAttributes(copyGroupAttributes)
            .copyListGroupAsMember(copyListMembershipsInOtherGroups)
            .copyListMembersOfGroup(copyListMemberships)
            .copyPrivilegesOfGroup(copyGroupPrivileges)
            .copyPrivilegesOfStem(copyFolderPrivs)
            .copyGroupAsPrivilege(copyPrivsInOtherGroups).save();

      } catch (InsufficientPrivilegeException ipe) {
        
        LOG.warn("Insufficient privilege exception for stem copy: " + SubjectHelper.getPretty(loggedInSubject), ipe);
        
        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
            TextContainer.retrieveFromRequest().getText().get("stemCopyInsufficientPrivileges")));
        return;

      }

      boolean changed = false;
      
      //see if we are changing the extension
      if (!StringUtils.equals(newStem.getExtension(), extension)) {
        newStem.setExtension(extension, false);
        changed = true;
      }
      
      //see if we are changing the display extension
      if (!StringUtils.equals(newStem.getDisplayExtension(), displayExtension)) {
        newStem.setDisplayExtension(displayExtension);
        changed = true;
      }

      //save it if we need to
      if (changed) {
        newStem.store();
      }
      
      guiResponseJs.addAction(GuiScreenAction.newScript("guiV2link('operation=UiV2Stem.viewStem&stemId=" + newStem.getId() + "')"));

      //lets show a success message on the new screen
      guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.success, 
          TextContainer.retrieveFromRequest().getText().get("stemCopySuccess")));
      

    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }

  }
  
  
  /**
   * 
   * @param request
   * @param response
   */
  public void addToMyFavorites(HttpServletRequest request, HttpServletResponse response) {
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();

    GrouperSession grouperSession = null;

    Stem stem = null;

    try {

      grouperSession = GrouperSession.start(loggedInSubject);

      stem = retrieveStemHelper(request, false).getStem();
    
      if (stem == null) {
        return;
      }

      GrouperUserDataApi.favoriteStemAdd(GrouperUiUserData.grouperUiGroupNameForUserData(), 
          loggedInSubject, stem);

      GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();
      
      guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.success, 
          TextContainer.retrieveFromRequest().getText().get("stemSuccessAddedToMyFavorites")));

      //redisplay so the button will change
      guiResponseJs.addAction(GuiScreenAction.newInnerHtmlFromJsp("#stemMoreActionsButtonContentsDivId", 
          "/WEB-INF/grouperUi2/stem/stemMoreActionsButtonContents.jsp"));

    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }

  }
  

  /**
   * ajax logic to remove from my favorites
   * @param request
   * @param response
   */
  public void removeFromMyFavorites(HttpServletRequest request, HttpServletResponse response) {
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
    
    GrouperSession grouperSession = null;

    Stem stem = null;

    try {

      grouperSession = GrouperSession.start(loggedInSubject);

      stem = retrieveStemHelper(request, false).getStem();

      if (stem == null) {
        return;
      }

      GrouperUserDataApi.favoriteStemRemove(GrouperUiUserData.grouperUiGroupNameForUserData(), 
          loggedInSubject, stem);

      GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();
      
      guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.success, 
          TextContainer.retrieveFromRequest().getText().get("stemSuccessRemovedFromMyFavorites")));

      //redisplay so the button will change
      guiResponseJs.addAction(GuiScreenAction.newInnerHtmlFromJsp("#stemMoreActionsButtonContentsDivId", 
          "/WEB-INF/grouperUi2/stem/stemMoreActionsButtonContents.jsp"));

    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }

  }

  /**
   * the filter button was pressed, or paging or sorting or something
   * @param request
   * @param response
   */
  public void filter(HttpServletRequest request, HttpServletResponse response) {
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
        
    GrouperSession grouperSession = null;
    
    try {
      grouperSession = GrouperSession.start(loggedInSubject);


      Stem stem = retrieveStemHelper(request, false).getStem();
      
      if (stem == null) {
        return;
      }

      filterHelper(request, response, stem);
      
    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
    
  }

  /**
   * submit the main form on the privilege screen which can do batch operations on a number of rows
   * @param request
   * @param response
   */
  public void assignPrivilegeBatch(HttpServletRequest request, HttpServletResponse response) {

    GrouperRequestContainer grouperRequestContainer = GrouperRequestContainer.retrieveFromRequestOrCreate();

    GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();

    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();

    GrouperSession grouperSession = null;
    
    try {
      grouperSession = GrouperSession.start(loggedInSubject);

      Stem stem = retrieveStemHelper(request, true).getStem();

      if (stem == null) {
        return;
      }

      StemContainer stemContainer = grouperRequestContainer.getStemContainer();

      //UiV2Stem.assignPrivilegeBatch?stemId=${grouperRequestContainer.stemContainer.guiStem.stem.id}
      
      String stemPrivilegeBatchUpdateOperation = request.getParameter("stemPrivilegeBatchUpdateOperation");
      Pattern operationPattern = Pattern.compile("^(assign|revoke)_(.*)$");
      Matcher operationMatcher = operationPattern.matcher(stemPrivilegeBatchUpdateOperation);
      if (!operationMatcher.matches()) {
        throw new RuntimeException("Invalid submission, should have a valid operation: '" + stemPrivilegeBatchUpdateOperation + "'");
      }
      
      String assignOrRevokeString = operationMatcher.group(1);
      boolean assign = StringUtils.equals("assign", assignOrRevokeString);
      if (!assign && !StringUtils.equals("revoke", assignOrRevokeString)) {
        throw new RuntimeException("Cant find assign or revoke: '" + assignOrRevokeString + "'");
      }
      String fieldName = operationMatcher.group(2);
      
      boolean assignAll = StringUtils.equals(fieldName, "all");
      
      //lets see how many are on a page
      String pageSizeString = request.getParameter("pagingTagPageSize");
      int pageSize = GrouperUtil.intValue(pageSizeString);
      
      //lets loop and get all the checkboxes
      Set<Member> members = new LinkedHashSet<Member>();
      
      //loop through all the checkboxes and collect all the members
      for (int i=0;i<pageSize;i++) {
        String memberId = request.getParameter("privilegeSubjectRow_" + i + "[]");
        if (!StringUtils.isBlank(memberId)) {
          Member member = MemberFinder.findByUuid(grouperSession, memberId, true);
          members.add(member);
        }
      }

      if (GrouperUtil.length(members) == 0) {

        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
            TextContainer.retrieveFromRequest().getText().get("stemErrorEntityRequired")));
        return;
      }
      
      int changes = 0;
      
      Privilege[] privileges = assignAll ? (assign ? new Privilege[]{
          NamingPrivilege.listToPriv(Field.FIELD_NAME_STEMMERS)} : new Privilege[]{
          NamingPrivilege.listToPriv(Field.FIELD_NAME_CREATORS),
          NamingPrivilege.listToPriv(Field.FIELD_NAME_STEMMERS),
          NamingPrivilege.listToPriv(Field.FIELD_NAME_STEM_ATTR_READERS),
          NamingPrivilege.listToPriv(Field.FIELD_NAME_STEM_ATTR_UPDATERS)
          } ) : new Privilege[]{NamingPrivilege.listToPriv(fieldName)};
      
      for (Member member : members) {
        
        for (Privilege privilege : privileges) {
          if (assign) {
            changes += stem.grantPriv(member.getSubject(), privilege, false) ? 1 : 0;
          } else {
            changes += stem.revokePriv(member.getSubject(), privilege, false) ? 1 : 0;
          }
        }
      }
      
      //reset the data (not really necessary, just in case)
      stemContainer.setPrivilegeGuiMembershipSubjectContainers(null);

      if (changes > 0) {
        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.success, 
            TextContainer.retrieveFromRequest().getText().get(
                assign ? "stemSuccessGrantedPrivileges" : "stemSuccessRevokedPrivileges")));
      } else {
        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.info, 
            TextContainer.retrieveFromRequest().getText().get(
                assign ? "stemNoteNoGrantedPrivileges" : "stemNoteNoRevokedPrivileges")));
        
      }
      guiResponseJs.addAction(GuiScreenAction.newScript("guiScrollTop()"));

      filterPrivilegesHelper(request, response, stem);

    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }

  }
  
  /**
   * assign or remove a privilege from a user, and redraw the filter screen... put a success at top
   * @param request
   * @param response
   */
  public void assignPrivilege(HttpServletRequest request, HttpServletResponse response) {

    GrouperRequestContainer grouperRequestContainer = GrouperRequestContainer.retrieveFromRequestOrCreate();

    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();

    GrouperSession grouperSession = null;
    
    try {
      grouperSession = GrouperSession.start(loggedInSubject);

      Stem stem = retrieveStemHelper(request, true).getStem();

      if (stem == null) {
        return;
      }

      StemContainer stemContainer = grouperRequestContainer.getStemContainer();

      //?assign=false&stemId=${grouperRequestContainer.stemContainer.guiStem.stem.id}&fieldName=${fieldName}&memberId=${guiMembershipSubjectContainer.guiMember.member.uuid}
      String assignString = request.getParameter("assign");
      boolean assign = GrouperUtil.booleanValue(assignString);
      String fieldName = request.getParameter("fieldName");
      String memberId = request.getParameter("memberId");

      Member member = MemberFinder.findByUuid(grouperSession, memberId, true);
      
      Privilege privilege = NamingPrivilege.listToPriv(fieldName);
      
      if (privilege == null) {
        throw new RuntimeException("Why is privilege not found???? " + fieldName);
      }
      
      //if someone revoked in the meantime, who cares...
      if (assign) {
        stem.grantPriv(member.getSubject(), privilege, false);
        
        //set a success message
        //messes up screen
        //guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.success, 
        //    TextContainer.retrieveFromRequest().getText().get("stemSuccessGrantedPrivilege")));
        
      } else {
        stem.revokePriv(member.getSubject(), privilege, false);
        
        //messes up screen
        //set a success message
        //guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.success, 
        //    TextContainer.retrieveFromRequest().getText().get("stemSuccessRevokedPrivilege")));
      }

      //reset the data (not really necessary, just in case)
      stemContainer.setPrivilegeGuiMembershipSubjectContainers(null);
      
      
      filterPrivilegesHelper(request, response, stem);

    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }


  }

  /**
   * the filter button was pressed, or paging or sorting, or view Stem or something
   * @param request
   * @param response
   */
  private void filterHelper(HttpServletRequest request, HttpServletResponse response, Stem stem) {
    
    GrouperRequestContainer grouperRequestContainer = GrouperRequestContainer.retrieveFromRequestOrCreate();
    GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();
    
    StemContainer stemContainer = grouperRequestContainer.getStemContainer();
    stemContainer.setGuiStem(new GuiStem(stem));      
    
    String filterText = request.getParameter("filterText");
    stemContainer.setFilterText(filterText);
    
    String pageSizeString = request.getParameter("pagingTagPageSize");
    int pageSize = -1;
    if (!StringUtils.isBlank(pageSizeString)) {
      pageSize = GrouperUtil.intValue(pageSizeString);
    } else {
      pageSize = GrouperUiConfig.retrieveConfig().propertyValueInt("pager.pagesize.default", 50);
    }
    stemContainer.getGuiPaging().setPageSize(pageSize);
    
    //1 indexed
    String pageNumberString = request.getParameter("pagingTagPageNumber");
    
    int pageNumber = 1;
    if (!StringUtils.isBlank(pageNumberString)) {
      pageNumber = GrouperUtil.intValue(pageNumberString);
    }
    
    stemContainer.getGuiPaging().setPageNumber(pageNumber);
    
    QueryOptions queryOptions = QueryOptions.create("displayExtension", true, pageNumber, pageSize);
    
    GrouperObjectFinder grouperObjectFinder = new GrouperObjectFinder()
      .assignObjectPrivilege(ObjectPrivilege.view)
      .assignParentStemId(stem.getId())
      .assignQueryOptions(queryOptions)
      .assignSplitScope(true).assignStemScope(Scope.ONE)
      .assignSubject(GrouperSession.staticGrouperSession().getSubject());

    if (!StringUtils.isBlank(filterText)) {
      grouperObjectFinder.assignFilterText(filterText);
    }

    Set<GrouperObject> results = grouperObjectFinder.findGrouperObjects();
    
    stemContainer.setChildGuiObjectsAbbreviated(GuiObjectBase.convertFromGrouperObjects(results));
    
    stemContainer.getGuiPaging().setTotalRecordCount(queryOptions.getQueryPaging().getTotalRecordCount());
    
    guiResponseJs.addAction(GuiScreenAction.newInnerHtmlFromJsp("#stemFilterResultsId", 
        "/WEB-INF/grouperUi2/stem/stemContents.jsp"));

  }
  
  /**
   * view stem
   * @param request
   * @param response
   */
  public void viewStem(HttpServletRequest request, HttpServletResponse response) {
    
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
    
    GrouperSession grouperSession = null;

    Stem stem = null;

    try {

      grouperSession = GrouperSession.start(loggedInSubject);

      stem = retrieveStemHelper(request, false).getStem();
      
      if (stem == null) {
        return;
      }

      GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();
      
      guiResponseJs.addAction(GuiScreenAction.newInnerHtmlFromJsp("#grouperMainContentDivId", 
          "/WEB-INF/grouperUi2/stem/viewStem.jsp"));
      
      if (GrouperRequestContainer.retrieveFromRequestOrCreate().getStemContainer().isCanAdminPrivileges()) {
        filterHelper(request, response, stem);
      }
    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
  }

  /**
   * results from retrieving results
   *
   */
  public static class RetrieveStemHelperResult {

    /**
     * stem
     */
    private Stem stem;

    /**
     * stem
     * @return stem
     */
    public Stem getStem() {
      return this.stem;
    }

    /**
     * stem
     * @param stem1
     */
    public void setStem(Stem stem1) {
      this.stem = stem1;
    }
    
    /**
     * if added error to screen
     */
    private boolean addedError;

    /**
     * if added error to screen
     * @return if error
     */
    @SuppressWarnings("unused")
    public boolean isAddedError() {
      return this.addedError;
    }

    /**
     * if added error to screen
     * @param addedError1
     */
    public void setAddedError(boolean addedError1) {
      this.addedError = addedError1;
    }
    
    
    
  }

  /**
   * get the stem from the request where the stem is required and require stem privilege is either needed or not
   * @param request
   * @param requireStemPrivilege
   * @return the stem finder result
   */
  public static RetrieveStemHelperResult retrieveStemHelper(HttpServletRequest request, boolean requireStemPrivilege) {
    return retrieveStemHelper(request, requireStemPrivilege, false, true);
  }

  /**
   * get the stem from the request
   * @param request
   * @param requireStemPrivilege
   * @return the stem finder result
   */
  public static RetrieveStemHelperResult retrieveStemHelper(HttpServletRequest request, boolean requireStemPrivilege, 
      boolean requireCreateGroupPrivilege, boolean requireStem) {

    //initialize the bean
    GrouperRequestContainer grouperRequestContainer = GrouperRequestContainer.retrieveFromRequestOrCreate();
    
    RetrieveStemHelperResult result = new RetrieveStemHelperResult();

    GrouperSession grouperSession = GrouperSession.staticGrouperSession();
    
    Stem stem = null;

    GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();

    String stemId = request.getParameter("stemId");
    String stemIndex = request.getParameter("stemIndex");
    String stemName = request.getParameter("stemName");
    
    boolean addedError = false;
    
    if (!StringUtils.isBlank(stemId)) {
      if (StringUtils.equals("root", stemId)) {
        stem = StemFinder.findRootStem(grouperSession);
      } else {
        stem = StemFinder.findByUuid(grouperSession, stemId, false);
      }
    } else if (!StringUtils.isBlank(stemName)) {
      stem = StemFinder.findByName(grouperSession, stemName, false);
    } else if (!StringUtils.isBlank(stemIndex)) {
      long idIndex = GrouperUtil.longValue(stemIndex);
      stem = StemFinder.findByIdIndex(idIndex, false, null);
    } else {
      
      if (!requireStem) {
        return result;
      }
      
      guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
          TextContainer.retrieveFromRequest().getText().get("stemCantFindStemId")));
      addedError = true;
    }

    if (stem != null) {
      grouperRequestContainer.getStemContainer().setGuiStem(new GuiStem(stem));      

      if (requireStemPrivilege && !grouperRequestContainer.getStemContainer().isCanAdminPrivileges()) {
        
        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
            TextContainer.retrieveFromRequest().getText().get("stemNotAllowedToAdminStem")));
        addedError = true;

      } else if (requireCreateGroupPrivilege && !grouperRequestContainer.getStemContainer().isCanCreateGroups()) {

        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
            TextContainer.retrieveFromRequest().getText().get("stemNotAllowedToCreateGroupsStem")));
        addedError = true;

      } else {
        result.setStem(stem);
      }

    } else {

      if (!requireStem) {
        return result;
      }

      if (!addedError && (!StringUtils.isBlank(stemId) || !StringUtils.isBlank(stemName) || !StringUtils.isBlank(stemIndex))) {
        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
            TextContainer.retrieveFromRequest().getText().get("stemCantFindStem")));
        addedError = true;
      }
      
    }
    result.setAddedError(addedError);
    
    //go back to the main screen, cant find stem
    if (addedError) {
      guiResponseJs.addAction(GuiScreenAction.newInnerHtmlFromJsp("#grouperMainContentDivId", 
          "/WEB-INF/grouperUi2/index/indexMain.jsp"));
    }
    
    return result;
  }

  /**
   * view stem privileges
   * @param request
   * @param response
   */
  public void stemPrivileges(HttpServletRequest request, HttpServletResponse response) {
    
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
    
    GrouperSession grouperSession = null;

    Stem stem = null;

    try {

      grouperSession = GrouperSession.start(loggedInSubject);

      stem = retrieveStemHelper(request, true).getStem();
      
      if (stem == null) {
        return;
      }

      GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();

      guiResponseJs.addAction(GuiScreenAction.newInnerHtmlFromJsp("#grouperMainContentDivId", 
          "/WEB-INF/grouperUi2/stem/stemPrivileges.jsp"));
      filterPrivilegesHelper(request, response, stem);

    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
  }


  /**
   * the filter button was pressed for privileges, or paging or sorting, or view Stem privileges or something
   * @param request
   * @param response
   */
  private void filterPrivilegesHelper(HttpServletRequest request, HttpServletResponse response, Stem stem) {
    
    GrouperRequestContainer grouperRequestContainer = GrouperRequestContainer.retrieveFromRequestOrCreate();

    GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();

    //if filtering text in subjects
    String filterText = request.getParameter("privilegeFilterText");
    grouperRequestContainer.getStemContainer().setPrivilegeFilterText(filterText);
    
    String privilegeFieldName = request.getParameter("privilegeField");
    if (!StringUtils.isBlank(privilegeFieldName)) {
      Field field = FieldFinder.find(privilegeFieldName, true);
      grouperRequestContainer.getStemContainer().setPrivilegeField(field);
    }
    
    //if filtering by subjects that have a certain type
    String membershipTypeString = request.getParameter("privilegeMembershipType");
    if (!StringUtils.isBlank(membershipTypeString)) {
      MembershipType membershipType = MembershipType.valueOfIgnoreCase(membershipTypeString, true);
      grouperRequestContainer.getStemContainer().setPrivilegeMembershipType(membershipType);
    }
    
    //how many per page
    String pageSizeString = request.getParameter("pagingTagPageSize");
    int pageSize = -1;
    if (!StringUtils.isBlank(pageSizeString)) {
      pageSize = GrouperUtil.intValue(pageSizeString);
    } else {
      pageSize = GrouperUiConfig.retrieveConfig().propertyValueInt("pager.pagesize.default", 50);
    }
    grouperRequestContainer.getStemContainer().getPrivilegeGuiPaging().setPageSize(pageSize);
    
    //1 indexed
    String pageNumberString = request.getParameter("pagingTagPageNumber");
    
    int pageNumber = 1;
    if (!StringUtils.isBlank(pageNumberString)) {
      pageNumber = GrouperUtil.intValue(pageNumberString);
    }
    
    grouperRequestContainer.getStemContainer().getPrivilegeGuiPaging().setPageNumber(pageNumber);
    
    
    guiResponseJs.addAction(GuiScreenAction.newInnerHtmlFromJsp("#stemPrivilegeFilterResultsId", 
        "/WEB-INF/grouperUi2/stem/stemPrivilegeContents.jsp"));
  
  }


  /**
   * the filter button for privileges was pressed, or paging or sorting or something
   * @param request
   * @param response
   */
  public void filterPrivileges(HttpServletRequest request, HttpServletResponse response) {
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
    
    GrouperSession grouperSession = null;
    
    try {
      grouperSession = GrouperSession.start(loggedInSubject);


      Stem stem = retrieveStemHelper(request, true).getStem();
      
      if (stem == null) {
        return;
      }

      filterPrivilegesHelper(request, response, stem);
      
    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
    
  }


  /**
   * copy stem
   * @param request
   * @param response
   */
  public void stemCopy(HttpServletRequest request, HttpServletResponse response) {
    
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
    
    GrouperSession grouperSession = null;
  
    Stem stem = null;
  
    try {
  
      grouperSession = GrouperSession.start(loggedInSubject);
  
      stem = retrieveStemHelper(request, true).getStem();
      
      if (stem == null) {
        return;
      }
  
      GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();
      
      guiResponseJs.addAction(GuiScreenAction.newInnerHtmlFromJsp("#grouperMainContentDivId", 
          "/WEB-INF/grouperUi2/stem/stemCopy.jsp"));

    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
  }

  /**
   * move stem
   * @param request
   * @param response
   */
  public void stemMove(HttpServletRequest request, HttpServletResponse response) {
    
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
    
    GrouperSession grouperSession = null;
  
    Stem stem = null;
  
    try {

      grouperSession = GrouperSession.start(loggedInSubject);
  
      stem = retrieveStemHelper(request, true).getStem();
      
      if (stem == null) {
        return;
      }
  
      GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();
      
      guiResponseJs.addAction(GuiScreenAction.newInnerHtmlFromJsp("#grouperMainContentDivId", 
          "/WEB-INF/grouperUi2/stem/stemMove.jsp"));

    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
  }

  /**
   * 
   * @param request
   * @param response
   */
  public void stemMoveSubmit(HttpServletRequest request, HttpServletResponse response) {
  
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
  
    GrouperSession grouperSession = null;
  
    Stem stem = null;
  
    try {
  
      grouperSession = GrouperSession.start(loggedInSubject);
  
      stem = retrieveStemHelper(request, true).getStem();
    
      if (stem == null) {
        return;
      }
  
      GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();
  
      String parentFolderId = request.getParameter("parentFolderComboName");
      
      //just get what they typed in
      if (StringUtils.isBlank(parentFolderId)) {
        parentFolderId = request.getParameter("parentFolderComboNameDisplay");
      }
      
      boolean moveChangeAlternateNames = GrouperUtil.booleanValue(request.getParameter("moveChangeAlternateNames[]"), false);
      
      final Stem parentFolder = new StemFinder().addPrivilege(NamingPrivilege.STEM)
          .assignSubject(loggedInSubject)
          .assignScope(parentFolderId).assignFindByUuidOrName(true).findStem();
      
      if (parentFolder == null) {
        
        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
            TextContainer.retrieveFromRequest().getText().get("stemCopyCantFindParentStemId")));
        return;
        
      }
  
      //MCH 20131224: dont need this since we are searching by stemmed folders above
      
      try {
  
        //get the new folder that was created
        new StemMove(stem, parentFolder).assignAlternateName(moveChangeAlternateNames).save();
  
      } catch (InsufficientPrivilegeException ipe) {
        
        LOG.warn("Insufficient privilege exception for stem move: " + SubjectHelper.getPretty(loggedInSubject), ipe);
        
        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
            TextContainer.retrieveFromRequest().getText().get("stemMoveInsufficientPrivileges")));
        return;
  
      }
      
      //go to the view stem screen
      guiResponseJs.addAction(GuiScreenAction.newScript("guiV2link('operation=UiV2Stem.viewStem&stemId=" + stem.getId() + "')"));
  
      //lets show a success message on the new screen
      guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.success, 
          TextContainer.retrieveFromRequest().getText().get("stemMoveSuccess")));
      
  
    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
  
  }

  /**
   * delete stem (show confirm screen)
   * @param request
   * @param response
   */
  public void stemDelete(HttpServletRequest request, HttpServletResponse response) {
    
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
    
    GrouperSession grouperSession = null;
  
    Stem stem = null;
  
    try {
  
      grouperSession = GrouperSession.start(loggedInSubject);
  
      stem = retrieveStemHelper(request, true).getStem();
      
      if (stem == null) {
        return;
      }
  
      GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();
      
      guiResponseJs.addAction(GuiScreenAction.newInnerHtmlFromJsp("#grouperMainContentDivId", 
          "/WEB-INF/grouperUi2/stem/stemDelete.jsp"));
  
    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
  }

  /**
   * hit submit on the delete stem screen
   * @param request
   * @param response
   */
  public void stemDeleteSubmit(HttpServletRequest request, HttpServletResponse response) {
  
    final Subject loggedInSubject = GrouperUiFilter.retrieveSubjectLoggedIn();
  
    GrouperSession grouperSession = null;
  
    Stem stem = null;
  
    try {
  
      grouperSession = GrouperSession.start(loggedInSubject);
  
      stem = retrieveStemHelper(request, true).getStem();
    
      if (stem == null) {
        return;
      }
  
      GuiResponseJs guiResponseJs = GuiResponseJs.retrieveGuiResponseJs();

      try {
  
        //get the new folder that was created
        stem.delete();
  
      } catch (InsufficientPrivilegeException ipe) {
        
        LOG.warn("Insufficient privilege exception for stem delete: " + SubjectHelper.getPretty(loggedInSubject), ipe);
        
        //go to the view stem screen
        guiResponseJs.addAction(GuiScreenAction.newScript("guiV2link('operation=UiV2Stem.viewStem&stemId=" + stem.getId() + "')"));

        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
            TextContainer.retrieveFromRequest().getText().get("stemDeleteInsufficientPrivileges")));
        return;
  
      } catch (StemDeleteException sde) {
        
        LOG.warn("Error deleting stem: " + SubjectHelper.getPretty(loggedInSubject) + ", " + stem, sde);
        
        //go to the view stem screen
        guiResponseJs.addAction(GuiScreenAction.newScript("guiV2link('operation=UiV2Stem.viewStem&stemId=" + stem.getId() + "')"));

        guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.error, 
            TextContainer.retrieveFromRequest().getText().get("stemErrorCantDelete")));

        return;
  
      }
      
      //go to the view stem screen for the parent since this stem is deleted
      guiResponseJs.addAction(GuiScreenAction.newScript("guiV2link('operation=UiV2Stem.viewStem&stemId=" + stem.getParentUuid() + "')"));
  
      //lets show a success message on the new screen
      guiResponseJs.addAction(GuiScreenAction.newMessage(GuiMessageType.success, 
          TextContainer.retrieveFromRequest().getText().get("stemDeleteSuccess")));
      
  
    } finally {
      GrouperSession.stopQuietly(grouperSession);
    }
  
  }

  /**
   * combo filter create group folder
   * @param request
   * @param response
   */
  public void createGroupParentFolderFilter(final HttpServletRequest request, HttpServletResponse response) {
  
    //run the combo logic
    DojoComboLogic.logic(request, response, new DojoComboQueryLogicBase<Stem>() {
  
      /**
       * 
       */
      @Override
      public Stem lookup(HttpServletRequest request, GrouperSession grouperSession, String query) {
        Subject loggedInSubject = grouperSession.getSubject();
        Stem theStem = new StemFinder().addPrivilege(NamingPrivilege.CREATE).assignSubject(loggedInSubject)
            .assignFindByUuidOrName(true).assignScope(query).findStem();
        return theStem;
      }
  
      /**
       * 
       */
      @Override
      public Collection<Stem> search(HttpServletRequest request, GrouperSession grouperSession, String query) {
        Subject loggedInSubject = grouperSession.getSubject();
        int stemComboSize = GrouperUiConfig.retrieveConfig().propertyValueInt("uiV2.stemComboboxResultSize", 200);
        QueryOptions queryOptions = QueryOptions.create(null, null, 1, stemComboSize);
        return new StemFinder().addPrivilege(NamingPrivilege.CREATE).assignScope(query).assignSubject(loggedInSubject)
            .assignSplitScope(true).assignQueryOptions(queryOptions).findStems();
      }
  
      /**
       * 
       * @param t
       * @return
       */
      @Override
      public String retrieveId(GrouperSession grouperSession, Stem t) {
        return t.getId();
      }
      
      /**
       * 
       */
      @Override
      public String retrieveLabel(GrouperSession grouperSession, Stem t) {
        return t.getDisplayName();
      }
  
      /**
       * 
       */
      @Override
      public String retrieveHtmlLabel(GrouperSession grouperSession, Stem t) {
        //description could be null?
        String label = GrouperUiUtils.escapeHtml(t.getDisplayName(), true);
        String htmlLabel = "<img src=\"../../grouperExternal/public/assets/images/folder.gif\" /> " + label;
        return htmlLabel;
      }
  
    });
    
  }

}

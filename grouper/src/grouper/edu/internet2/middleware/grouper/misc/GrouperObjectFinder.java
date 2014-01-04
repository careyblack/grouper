/**
 * 
 */
package edu.internet2.middleware.grouper.misc;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.lang.StringUtils;

import edu.internet2.middleware.grouper.Group;
import edu.internet2.middleware.grouper.GroupFinder;
import edu.internet2.middleware.grouper.Stem;
import edu.internet2.middleware.grouper.StemFinder;
import edu.internet2.middleware.grouper.SubjectFinder;
import edu.internet2.middleware.grouper.Stem.Scope;
import edu.internet2.middleware.grouper.attr.AttributeDef;
import edu.internet2.middleware.grouper.attr.AttributeDefName;
import edu.internet2.middleware.grouper.attr.finder.AttributeDefFinder;
import edu.internet2.middleware.grouper.attr.finder.AttributeDefNameFinder;
import edu.internet2.middleware.grouper.internal.dao.QueryOptions;
import edu.internet2.middleware.grouper.internal.dao.QueryPaging;
import edu.internet2.middleware.grouper.internal.dao.QuerySortField;
import edu.internet2.middleware.grouper.privs.AccessPrivilege;
import edu.internet2.middleware.grouper.privs.AttributeDefPrivilege;
import edu.internet2.middleware.grouper.privs.NamingPrivilege;
import edu.internet2.middleware.grouper.privs.Privilege;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.subject.Source;
import edu.internet2.middleware.subject.Subject;
import edu.internet2.middleware.subject.provider.SourceManager;


/**
 * find object of multiple types, and allow paging
 * @author mchyzer
 *
 */
public class GrouperObjectFinder {

  /**
   * which privileges should be used, see the enum for examples
   */
  private ObjectPrivilege objectPrivilege;

  /**
   * which privileges should be used, see the enum for examples
   * @param theObjectPrivilege
   * @return this for chaining
   */
  public GrouperObjectFinder assignObjectPrivilege(ObjectPrivilege theObjectPrivilege) {
    this.objectPrivilege = theObjectPrivilege;
    return this;
  }
  
  /**
   * privilege type to query
   *
   */
  public static enum ObjectPrivilege {
    
    /**
     * objects that the grouper session can see
     */
    view {

      /**
       * @see ObjectPrivilege#stemPrivileges()
       */
      @Override
      public Set<Privilege> stemPrivileges() {
        //no privileges (all)
        return null;
      }

      /**
       * @see ObjectPrivilege#groupPrivileges()
       */
      @Override
      public Set<Privilege> groupPrivileges() {
        return AccessPrivilege.VIEW_PRIVILEGES;
      }

      /**
       * @see ObjectPrivilege#attributeDefPrivileges()
       */
      @Override
      public Set<Privilege> attributeDefPrivileges() {
        return AttributeDefPrivilege.VIEW_PRIVILEGES;
      }
    }, 
    
    /**
     * objects where is there is a read privilege the grouper session can read
     */
    read {

      /**
       * @see ObjectPrivilege#stemPrivileges()
       */
      @Override
      public Set<Privilege> stemPrivileges() {
        //no privileges (all)
        return null;
      }

      /**
       * @see ObjectPrivilege#groupPrivileges()
       */
      @Override
      public Set<Privilege> groupPrivileges() {
        return AccessPrivilege.READ_PRIVILEGES;
      }

      /**
       * @see ObjectPrivilege#attributeDefPrivileges()
       */
      @Override
      public Set<Privilege> attributeDefPrivileges() {
        return AttributeDefPrivilege.READ_PRIVILEGES;
      }
    },
    update {

      /**
       * @see ObjectPrivilege#stemPrivileges()
       */
      @Override
      public Set<Privilege> stemPrivileges() {
        return NamingPrivilege.CREATE_PRIVILEGES;
      }
      /**
       * @see ObjectPrivilege#groupPrivileges()
       */
      @Override
      public Set<Privilege> groupPrivileges() {
        return AccessPrivilege.UPDATE_PRIVILEGES;
      }

      /**
       * @see ObjectPrivilege#attributeDefPrivileges()
       */
      @Override
      public Set<Privilege> attributeDefPrivileges() {
        return AttributeDefPrivilege.MANAGE_PRIVILEGES;
      }
    }, 
    admin {

      /**
       * @see ObjectPrivilege#stemPrivileges()
       */
      @Override
      public Set<Privilege> stemPrivileges() {
        return NamingPrivilege.ADMIN_PRIVILEGES;
      }
      /**
       * @see ObjectPrivilege#groupPrivileges()
       */
      @Override
      public Set<Privilege> groupPrivileges() {
        return AccessPrivilege.ADMIN_PRIVILEGES;
      }

      /**
       * @see ObjectPrivilege#attributeDefPrivileges()
       */
      @Override
      public Set<Privilege> attributeDefPrivileges() {
        return AttributeDefPrivilege.ADMIN_PRIVILEGES;
      }
    };
    
    /**
     * privileges for stem queries
     * @return the privileges
     */
    public abstract Set<Privilege> stemPrivileges();
    
    /**
     * privileges for group queries
     * @return the privileges
     */
    public abstract Set<Privilege> groupPrivileges();
    
    /**
     * privileges for attributeDef queries
     * @return the privileges
     */
    public abstract Set<Privilege> attributeDefPrivileges();
    
  }
  
  /**
   * 
   */
  public GrouperObjectFinder() {
  }

  /**
   * figure out the paging for one set of objects in a multiset list
   * @param queryOptions
   * @param firstIndexOnPage
   * @param lastIndexOnPage
   * @param firstStemIndex
   * @param lastStemIndex
   * @return true if should run query, false if not
   */
  private static boolean decoratePaging(QueryOptions queryOptions, 
      int firstIndexOnPage, int lastIndexOnPage, int firstSetIndex, int lastSetIndex) {

    //  |---------------|
    //      |------|
    if ((firstIndexOnPage <= firstSetIndex && lastIndexOnPage >= lastSetIndex)) {
      //we dont need paging, get them all
      queryOptions.paging(null);
      return true;
    }
        
    if (queryOptions.getQueryPaging() == null) {
      queryOptions.paging(new QueryPaging());
    }

    //      56789012
    //        |---------------|
    //      |------|
    if ((firstSetIndex < firstIndexOnPage)
        && (lastSetIndex >= firstIndexOnPage && lastSetIndex <= lastIndexOnPage)) {
      
      //the page size
      queryOptions.getQueryPaging().setPageSize(1 + lastSetIndex - firstIndexOnPage);
      
      //we will start at the index where they overlap...
      queryOptions.getQueryPaging().setPageStartIndexQueryByIndex(firstIndexOnPage - firstSetIndex);
      return true;
    }
    
    //              56789012
    //  |---------------|
    //              |------|
    if ((firstSetIndex >= firstIndexOnPage && firstSetIndex <= lastIndexOnPage)
        && (lastSetIndex > lastIndexOnPage)) {
      
      //the page size
      queryOptions.getQueryPaging().setPageSize(1 + lastIndexOnPage - firstSetIndex);
      
      //we will start at 0
      queryOptions.getQueryPaging().setPageStartIndexQueryByIndex(0);
      
      return true;
    }
    
    
    
    //        567890123
    //        |-------|
    //      |------------|
    if (firstSetIndex < firstIndexOnPage && lastSetIndex > lastIndexOnPage) {
      
      //the page size is the total record count
      queryOptions.getQueryPaging().setPageSize(1 + lastIndexOnPage - firstIndexOnPage);
      
      //we will start at the index where they overlap...
      queryOptions.getQueryPaging().setPageStartIndexQueryByIndex(firstIndexOnPage - firstSetIndex);
      
      return true;
    }
    
    return false;
  }
  
  /**
   * subject to add to queries for example for privileges
   */
  private Subject subject; 

  /**
   * subject to add to queries for example for privileges
   * @param theSubject
   * @return this for chaining
   */
  public GrouperObjectFinder assignSubject(Subject theSubject) {
    this.subject = theSubject;
    return this;
  }

  /**
   * if only looking for objects in this stem, also set stemScope
   */
  private String parentStemId;
  
  /**
   * if only looking for objects in this stem, also set stemScope
   * @param theParentStemId
   * @return this for chaining
   */
  public GrouperObjectFinder assignParentStemId(String theParentStemId) {
    this.parentStemId = theParentStemId;
    return this;
  }
  
  /**
   * if only looking for objects in a stem, this is if ONE or SUB
   */
  private Scope stemScope;

  /**
   * if only looking for objects in a stem, this is if ONE or SUB
   * @param theStemScope
   * @return this for chaining
   */
  public GrouperObjectFinder assignStemScope(Scope theStemScope) {
    this.stemScope = theStemScope;
    return this;
  }
  
  /**
   * if filtering names by certain strings
   */
  private String filterText;

  /**
   * if filtering names by certain strings
   * @param theFilterText
   * @return this for chaining
   */
  public GrouperObjectFinder assignFilterText(String theFilterText) {
    this.filterText = theFilterText;
    return this;
  }
  
  /**
   * if the scope has spaces in it, then split by whitespace, and find results that contain all of the scope strings
   */
  private Boolean splitScope;

  /**
   * if the scope has spaces in it, then split by whitespace, and find results that contain all of the scope strings
   * @param theSplitScope
   * @return this for chaining
   */
  public GrouperObjectFinder assignSplitScope(boolean theSplitScope) {
    this.splitScope = theSplitScope;
    return this;
  }

  /**
   * if sorting / paging
   */
  private QueryOptions queryOptions;
  
  /**
   * if sorting / paging
   * @param theQueryOptions
   */
  public GrouperObjectFinder assignQueryOptions(QueryOptions theQueryOptions) {
    this.queryOptions = theQueryOptions;
    return this;
  }
  
  /**
   * find grouper objects based on the paramet
   * @return the set of objects
   */
  public Set<GrouperObject> findGrouperObjects() {
    //lets get the size of all objects
    int size = 0;
    
    Set<GrouperObject> results = new LinkedHashSet<GrouperObject>();
    
    QueryOptions countOptions = new QueryOptions();
    countOptions.retrieveResults(false);
    countOptions.retrieveCount(true);
    
    StemFinder stemFinder = new StemFinder()
      .assignQueryOptions(countOptions);

    GroupFinder groupFinder = new GroupFinder()
      .assignQueryOptions(countOptions);

    AttributeDefFinder attributeDefFinder = new AttributeDefFinder()
      .assignQueryOptions(countOptions);

    AttributeDefNameFinder attributeDefNameFinder = new AttributeDefNameFinder()
      .assignQueryOptions(countOptions);

    if (this.objectPrivilege != null) {
      if (GrouperUtil.length(this.objectPrivilege.stemPrivileges()) > 0) {
        stemFinder.assignPrivileges(this.objectPrivilege.stemPrivileges());
      }
      if (GrouperUtil.length(this.objectPrivilege.groupPrivileges()) > 0) {
        groupFinder.assignPrivileges(this.objectPrivilege.groupPrivileges());
      }
      if (GrouperUtil.length(this.objectPrivilege.attributeDefPrivileges()) > 0) {
        attributeDefFinder.assignPrivileges(this.objectPrivilege.attributeDefPrivileges());
      }
      if (GrouperUtil.length(this.objectPrivilege.attributeDefPrivileges()) > 0) {
        attributeDefNameFinder.assignPrivileges(this.objectPrivilege.attributeDefPrivileges());
      }
    }
    
    if (this.subject != null) {
      stemFinder.assignSubject(this.subject);
      groupFinder.assignSubject(this.subject);
      attributeDefFinder.assignSubject(this.subject);
      attributeDefNameFinder.assignSubject(this.subject);
    }
    
    if (!StringUtils.isBlank(this.parentStemId)) {
      stemFinder.assignParentStemId(this.parentStemId);
      groupFinder.assignParentStemId(this.parentStemId);
      attributeDefFinder.assignParentStemId(this.parentStemId);
      attributeDefNameFinder.assignParentStemId(this.parentStemId);
    }
    
    if (this.stemScope != null) {
      stemFinder.assignStemScope(this.stemScope);
      groupFinder.assignStemScope(this.stemScope);
      attributeDefFinder.assignStemScope(this.stemScope);
      attributeDefNameFinder.assignStemScope(this.stemScope);
    }
    
    if (!StringUtils.isBlank(this.filterText)) {
      String theFilterText = "%" + this.filterText;
      stemFinder.assignScope(theFilterText);
      groupFinder.assignScope(theFilterText);
      attributeDefFinder.assignScope(theFilterText);
      attributeDefNameFinder.assignScope(theFilterText);
      //default to true
      boolean theSplitScope = this.splitScope == null || this.splitScope;
      stemFinder.assignSplitScope(theSplitScope);
      groupFinder.assignSplitScope(theSplitScope);
      attributeDefFinder.assignSplitScope(theSplitScope);
      attributeDefNameFinder.assignSplitScope(theSplitScope);
    }
    
    boolean paging = this.queryOptions != null && this.queryOptions.getQueryPaging() != null;

    int firstIndexOnPage = -1;
    int lastIndexOnPage = -1;
    int stemSize = -1;
    int groupSize = -1;
    int attributeDefSize = -1;
    int attributeDefNameSize = -1;
    int subjectSize = -1;
    boolean retrieveSubjects = true;
    
    //if we are looking in a stem, then dont look for subjects
    if (!StringUtils.isBlank(this.parentStemId) || StringUtils.isBlank(this.filterText)) {
      retrieveSubjects = false;
    }
    
    //retrieve them all, we cant page
    Set<Subject> subjects = null;
    
    if (retrieveSubjects) {      
      
      //all sources except groups or entities
      Set<Source> sources = new HashSet<Source>();
      String gsaId = SubjectFinder.internal_getGSA().getId();
      Source esa = SubjectFinder.internal_getEntitySourceAdapter(false);
      String esaId = esa == null ? null : esa.getId();
      for (Source source : SourceManager.getInstance().getSources()) {
        if ( StringUtils.equals(source.getId(), gsaId  )
            || (!StringUtils.isBlank(esaId)
                && StringUtils.equals(source.getId(), esaId))) {
          continue;
        }
        sources.add(source);
      }

      subjects = SubjectFinder.findAll(this.filterText, sources);
    }

    if ((this.queryOptions != null && this.queryOptions.isRetrieveCount()) || paging) {

      stemFinder.findStems();
      
      stemSize = countOptions.getCount().intValue();
      size += countOptions.getCount();

      groupFinder.findGroups();
      
      groupSize = countOptions.getCount().intValue();
      size += countOptions.getCount();

      attributeDefFinder.findAttributes();

      attributeDefSize = countOptions.getCount().intValue();
      size += countOptions.getCount();

      attributeDefNameFinder.findAttributeNames();
      
      attributeDefNameSize = countOptions.getCount().intValue();
      size += countOptions.getCount();

      //subjects
      subjectSize = GrouperUtil.length(subjects);
      size += subjectSize;
      
      //total number of records
      if (this.queryOptions.getQueryPaging() != null) {
        this.queryOptions.getQueryPaging().setTotalRecordCount(size);
      }
      this.queryOptions.setCount(new Long(size));
      
      //if we are only here to get the count, get the count
      if (this.queryOptions.isRetrieveCount() && !this.queryOptions.isRetrieveResults()) {
        return results;
      }
      
      this.queryOptions.getQueryPaging().calculateIndexes();
      
      firstIndexOnPage = queryOptions.getQueryPaging().getFirstIndexOnPage();
      lastIndexOnPage = queryOptions.getQueryPaging().getLastIndexOnPage();

    }
    

    {
      
      int firstStemIndex = stemSize > 0 ? 0 : -1;
      int lastStemIndex = stemSize > 0 ? stemSize-1 : -1;
      
      QueryOptions stemQueryOptions = null;
      
      if (this.queryOptions != null) {
        stemQueryOptions = new QueryOptions();
        if (this.queryOptions.getQuerySort() != null) {
          stemQueryOptions.sort(this.queryOptions.getQuerySort().clone());
        }
      }
      
      if (!paging || decoratePaging(stemQueryOptions, firstIndexOnPage, lastIndexOnPage, firstStemIndex, lastStemIndex)) {

        stemFinder.assignQueryOptions(stemQueryOptions);

        Set<Stem> stems = stemFinder.findStems();
        results.addAll(stems);
        
      }
      
      
    }
    
    {
      int firstGroupIndex = groupSize > 0 ? stemSize : -1;
      int lastGroupIndex = groupSize > 0 ? stemSize+groupSize-1 : -1;

      QueryOptions groupQueryOptions = null;
      
      if (this.queryOptions != null) {
        groupQueryOptions = new QueryOptions();
        if (this.queryOptions.getQuerySort() != null) {
          groupQueryOptions.sort(this.queryOptions.getQuerySort().clone());
        }
      }

      if (!paging || decoratePaging(groupQueryOptions, firstIndexOnPage, lastIndexOnPage, firstGroupIndex, lastGroupIndex)) {
        groupFinder.assignQueryOptions(groupQueryOptions);

        Set<Group> groups = groupFinder.findGroups();
        results.addAll(groups);
      }
    }
    
    {
      int firstAttributeDefIndex = attributeDefSize > 0 ? groupSize + stemSize : -1;
      int lastAttributeDefIndex = attributeDefSize > 0 ? stemSize+groupSize+attributeDefSize-1 : -1;

      QueryOptions attributeDefQueryOptions = null;
      
      if (this.queryOptions != null) {
        attributeDefQueryOptions = new QueryOptions();
        if (this.queryOptions.getQuerySort() != null) {
          attributeDefQueryOptions.sort(this.queryOptions.getQuerySort().clone());
          
          //take out the display parts...
          List<QuerySortField> querySortFields = attributeDefQueryOptions.getQuerySort().getQuerySortFields();
          for (int i=0;i<querySortFields.size();i++) {
            querySortFields.get(i).setColumn(AttributeDef.massageSortField(querySortFields.get(i).getColumn()));
          }
        }
      }

      if (!paging || decoratePaging(attributeDefQueryOptions, firstIndexOnPage, lastIndexOnPage, firstAttributeDefIndex, 
          lastAttributeDefIndex)) {

        attributeDefFinder.assignQueryOptions(attributeDefQueryOptions);
        
        Set<AttributeDef> attributeDefSet = attributeDefFinder.findAttributes();
        
        results.addAll(attributeDefSet);
      }
    }

    {
      int firstAttributeDefNameIndex = attributeDefNameSize > 0 ? attributeDefSize + groupSize + stemSize : -1;
      int lastAttributeDefNameIndex = attributeDefNameSize > 0 ? attributeDefNameSize+stemSize+groupSize+attributeDefSize-1 : -1;

      QueryOptions attributeDefNameQueryOptions = null;
      
      if (this.queryOptions != null) {
        attributeDefNameQueryOptions = new QueryOptions();
        if (this.queryOptions.getQuerySort() != null) {
          attributeDefNameQueryOptions.sort(this.queryOptions.getQuerySort().clone());
          
          //take out the display parts...
          List<QuerySortField> querySortFields = attributeDefNameQueryOptions.getQuerySort().getQuerySortFields();
          for (int i=0;i<querySortFields.size();i++) {
            querySortFields.get(i).setColumn(AttributeDef.massageSortField(querySortFields.get(i).getColumn()));
          }
        }
      }

      if (!paging || decoratePaging(attributeDefNameQueryOptions, firstIndexOnPage, lastIndexOnPage, firstAttributeDefNameIndex, 
          lastAttributeDefNameIndex)) {
        attributeDefNameFinder.assignQueryOptions(attributeDefNameQueryOptions);

        Set<AttributeDefName> attributeDefNameSet = attributeDefNameFinder.findAttributeNames();
        
        results.addAll(attributeDefNameSet);
      }        
    }
    
    {
      int firstSubjectIndex = subjectSize > 0 ? attributeDefNameSize + attributeDefSize + groupSize + stemSize : -1;
      int lastSubjectIndex = subjectSize > 0 ? subjectSize+attributeDefNameSize+stemSize+groupSize+attributeDefSize-1 : -1;

      QueryOptions subjectQueryOptions = null;
      
      if (this.queryOptions != null) {
        subjectQueryOptions = new QueryOptions();
        if (this.queryOptions.getQuerySort() != null) {
          subjectQueryOptions.sort(this.queryOptions.getQuerySort().clone());
          
          //take out the display parts...
          List<QuerySortField> querySortFields = subjectQueryOptions.getQuerySort().getQuerySortFields();
          for (int i=0;i<querySortFields.size();i++) {
            querySortFields.get(i).setColumn(AttributeDef.massageSortField(querySortFields.get(i).getColumn()));
          }
        }
      }

      if (retrieveSubjects && (!paging || decoratePaging(subjectQueryOptions, firstIndexOnPage, lastIndexOnPage, firstSubjectIndex, 
          lastSubjectIndex))) {
        
        //sory by name (case insensitive)?  I guess
        Map<String, Subject> resultMap = new TreeMap<String, Subject>();
        
        for (Subject subject : GrouperUtil.nonNull(subjects)) {
          //concate source id and subject id since there could be multiple with same name
          resultMap.put(StringUtils.defaultString(subject.getName() + subject.getSourceId() + subject.getId()).toLowerCase(), subject);
        }
        
        int pageStartIndex = subjectQueryOptions == null || subjectQueryOptions.getQueryPaging() == null ? 
            1 : subjectQueryOptions.getQueryPaging().getPageStartIndex();
        int pageSize = subjectQueryOptions == null || subjectQueryOptions.getQueryPaging() == null ? 
            GrouperUtil.length(subjects) : subjectQueryOptions.getQueryPaging().getPageSize();
        
        int index = 0;
        int added = 0;
        //add the subjects which need to be added
        for (String sortString : resultMap.keySet()) {
          if (index >= pageStartIndex) {
            results.add(new GrouperObjectSubjectWrapper(resultMap.get(sortString)));
            added++;
            if (added >= pageSize) {
              break;
            }
          }
          index++;
        }
      }        
    }
    
    return results;
  }
  
}

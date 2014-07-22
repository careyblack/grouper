/**
 * Copyright 2014 Internet2
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
  Copyright (C) 2004-2007 University Corporation for Advanced Internet Development, Inc.
  Copyright (C) 2004-2007 The University Of Chicago

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
*/

package edu.internet2.middleware.grouper;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;

import edu.internet2.middleware.grouper.Stem.Scope;
import edu.internet2.middleware.grouper.exception.GroupNotFoundException;
import edu.internet2.middleware.grouper.exception.GrouperSessionException;
import edu.internet2.middleware.grouper.group.TypeOfGroup;
import edu.internet2.middleware.grouper.internal.dao.QueryOptions;
import edu.internet2.middleware.grouper.misc.E;
import edu.internet2.middleware.grouper.misc.GrouperDAOFactory;
import edu.internet2.middleware.grouper.misc.GrouperSessionHandler;
import edu.internet2.middleware.grouper.privs.Privilege;
import edu.internet2.middleware.grouper.privs.PrivilegeHelper;
import edu.internet2.middleware.grouper.util.GrouperUtil;
import edu.internet2.middleware.grouper.validator.NotNullValidator;
import edu.internet2.middleware.subject.Subject;

/**
 * Find groups within the Groups Registry.
 * <p/>
 * @author  blair christensen.
 * @version $Id: GroupFinder.java,v 1.62 2009-11-17 02:52:29 mchyzer Exp $
 */
public class GroupFinder {

  /**
   * if we are filtering for groups which are composite owners or not
   */
  private Boolean compositeOwner = null;

  /**
   * if we are filtering for groups which are composite owners or not
   * @param theCompositeOwner
   * @return this for chaining
   */
  public GroupFinder assignCompositeOwner(Boolean theCompositeOwner) {
    this.compositeOwner = theCompositeOwner;
    return this;
  }
  
  // PRIVATE CLASS CONSTANTS //
  /** error for finding by attribute */
  @SuppressWarnings("unused")
  private static final String ERR_FINDBYATTRIBUTE = "could not find group by attribute: ";

  /** error for finding by type */
  @SuppressWarnings("unused")
  private static final String ERR_FINDBYTYPE      = "could not find group by type: ";
  
  /**
   * Find <tt>Group</tt> by attribute value.
   * <pre class="eg">
   * try {
   *   Group g = GroupFinder.findByAttribute(s, "description", "some value");
   * }
   * catch (GroupNotFoundException eGNF) {
   * }
   * </pre>
   * @param   s     Search within this session context.
   * @param   attr  Search on this attribute.
   * @param   val   Search for this value.
   * @return  Matching {@link Group}.
   * @throws  GroupNotFoundException
   * @throws  IllegalArgumentException
   * @since   1.1.0
   * @deprecated use the overload
   */
  @Deprecated
  public static Group findByAttribute(GrouperSession s, String attr, String val)
    throws  GroupNotFoundException,
            IllegalArgumentException {

    return findByAttribute(s, attr, val, true);

  }

  /**
   * Find <tt>Group</tt> by attribute value.
   * <pre class="eg">
   *   Group g = GroupFinder.findByAttribute(s, "description", "some value", true);
   * </pre>
   * @param   s     Search within this session context.
   * @param   attr  Search on this attribute.
   * @param   val   Search for this value.
   * @param exceptionOnNull true if there should be an exception on null
   * @return  Matching {@link Group}.
   * @throws  GroupNotFoundException
   * @throws  IllegalArgumentException
   * @since   1.1.0
   */
  public static Group findByAttribute(GrouperSession s, String attr, String val, 
      boolean exceptionOnNull)
    throws  GroupNotFoundException, IllegalArgumentException {

    //note, no need for GrouperSession inverse of control
    NotNullValidator v = NotNullValidator.validate(s);
    if (v.isInvalid()) {
      throw new IllegalArgumentException("null session");
    }
    v = NotNullValidator.validate(attr);
    if (v.isInvalid()) {
      throw new IllegalArgumentException("null attribute");
    }
    v = NotNullValidator.validate(val);
    if (v.isInvalid()) {
      throw new IllegalArgumentException("null value");
    }
    
    final String ATTR = attr;
    final String VAL = val;
    final boolean EXCEPTION_ON_NULL = exceptionOnNull;

    return (Group)GrouperSession.callbackGrouperSession(s, new GrouperSessionHandler() {
      
      /**
       *
       */
      public Object callback(GrouperSession grouperSession)
          throws GrouperSessionException {
        return GrouperDAOFactory.getFactory().getGroup().findByAttribute(ATTR, VAL, EXCEPTION_ON_NULL, true);
      }
    });

  } 

  /**
   * Find <tt>Group</tt>s by attribute value.  Returns groups or empty set if none (never null)
   * <pre class="eg">
   *   Set<Group> groups = GroupFinder.findAllByAttribute(s, "description", "some value");
   * </pre>
   * @param   s     Search within this session context.
   * @param   attr  Search on this attribute.
   * @param   val   Search for this value.
   * @return  Matching {@link Group}.
   * @throws  IllegalArgumentException
   * @since   1.1.0
   */
  public static Set<Group> findAllByAttribute(GrouperSession s, String attr, String val)
      throws  IllegalArgumentException {
    //note, no need for GrouperSession inverse of control
    NotNullValidator v = NotNullValidator.validate(s);
    if (v.isInvalid()) {
      throw new IllegalArgumentException("null session");
    }
    v = NotNullValidator.validate(attr);
    if (v.isInvalid()) {
      throw new IllegalArgumentException("null attribute");
    }
    v = NotNullValidator.validate(val);
    if (v.isInvalid()) {
      throw new IllegalArgumentException("null value");
    }
    
    final String ATTR = attr;
    final String VAL = val;
    
    return (Set<Group>)GrouperSession.callbackGrouperSession(s, new GrouperSessionHandler() {
      
      /**
       *
       */
      public Object callback(GrouperSession grouperSession)
          throws GrouperSessionException {
        return GrouperDAOFactory.getFactory().getGroup().findAllByAttr(ATTR, VAL, null, true);
      }
    });
  } 

  /**
   * Find a group within the registry by name.
   * <pre class="eg">
   * try {
   *   Group g = GroupFinder.findByName(name);
   * }
   * catch (GroupNotFoundException e) {
   *   // Group not found
   * }
   * </pre>
   * @param   s     Find group within this session context.
   * @param   name  Name of group to find.
   * @return  A {@link Group}
   * @throws  GroupNotFoundException
   * @Deprecated use the overload
   */
  @Deprecated
  public static Group findByName(GrouperSession s, String name) 
      throws GroupNotFoundException {
    return findByName(s, name, true);
  } 

  /**
   * Find a group within the registry by name.
   * <pre class="eg">
   * try {
   *   Group g = GroupFinder.findByName(name);
   * }
   * catch (GroupNotFoundException e) {
   *   // Group not found
   * }
   * </pre>
   * @param   s     Find group within this session context.
   * @param   name  Name of group to find.
   * @param exceptionIfNotFound 
   * @return  A {@link Group}
   * @throws  GroupNotFoundException
   */
  public static Group findByName(GrouperSession s, String name, boolean exceptionIfNotFound) 
    throws GroupNotFoundException {
    return findByName(s, name, exceptionIfNotFound, null);
  }

  
  /**
   * Find a group within the registry by name.
   * <pre class="eg">
   * try {
   *   Group g = GroupFinder.findByName(name);
   * }
   * catch (GroupNotFoundException e) {
   *   // Group not found
   * }
   * </pre>
   * @param   s     Find group within this session context.
   * @param   name  Name of group to find.
   * @param exceptionIfNotFound 
   * @param queryOptions paging, sorting, caching options
   * @return  A {@link Group}
   * @throws  GroupNotFoundException
   */
  public static Group findByName(GrouperSession s, String name, boolean exceptionIfNotFound, QueryOptions queryOptions) 
    throws GroupNotFoundException {
    
    //note, no need for GrouperSession inverse of control
    GrouperSession.validate(s);
    Group g = null;
    g = GrouperDAOFactory.getFactory().getGroup().findByName(name, exceptionIfNotFound, queryOptions) ;
    
    if (g == null) {
      return g;
    }
    
    //2007-10-16: Gary Brown
    //https://bugs.internet2.edu/jira/browse/GRP-36
    //Ugly... and probably breaks the abstraction but quick and easy to 
    //remove when a more elegant solution found.
    if(s.getSubject().equals(SubjectFinder.findRootSubject()))
      return g;
    
    if ( PrivilegeHelper.canView( s.internal_getRootSession(), g, s.getSubject() ) ) {
      return g;
    }
    LOG.error(E.GF_FBNAME + E.CANNOT_VIEW + ", name: " + name);
    if (!exceptionIfNotFound) {
      return null;
    }
    throw new GroupNotFoundException(E.GROUP_NOTFOUND + " by name: " + name);
  } 

  /**
   * Find a group within the registry by its current name.
   * <pre class="eg">
   * try {
   *   Group g = GroupFinder.findByCurrentName(name, true);
   * }
   * catch (GroupNotFoundException e) {
   *   // Group not found
   * }
   * </pre>
   * @param   s     Find group within this session context.
   * @param   name  Name of group to find.
   * @param exceptionIfNotFound 
   * @return  A {@link Group}
   * @throws  GroupNotFoundException
   */
  public static Group findByCurrentName(GrouperSession s, String name, boolean exceptionIfNotFound) 
    throws GroupNotFoundException {
    
    //note, no need for GrouperSession inverse of control
    GrouperSession.validate(s);
    Group g = null;
    g = GrouperDAOFactory.getFactory().getGroup().findByCurrentName(name, exceptionIfNotFound) ;
    
    if (g == null) {
      return g;
    }
    
    //2007-10-16: Gary Brown
    //https://bugs.internet2.edu/jira/browse/GRP-36
    //Ugly... and probably breaks the abstraction but quick and easy to 
    //remove when a more elegant solution found.
    if(s.getSubject().equals(SubjectFinder.findRootSubject()))
      return g;
    
    if ( PrivilegeHelper.canView( s.internal_getRootSession(), g, s.getSubject() ) ) {
      return g;
    }
    LOG.error(E.GF_FBNAME + E.CANNOT_VIEW);
    if (!exceptionIfNotFound) {
      return null;
    }
    throw new GroupNotFoundException(E.GROUP_NOTFOUND + " by name: " + name);
  } 
  
  /**
   * Find a group within the registry by its alternate name.
   * <pre class="eg">
   * try {
   *   Group g = GroupFinder.findByAlternateName(name, true);
   * }
   * catch (GroupNotFoundException e) {
   *   // Group not found
   * }
   * </pre>
   * @param   s     Find group within this session context.
   * @param   name  Name of group to find.
   * @param exceptionIfNotFound 
   * @return  A {@link Group}
   * @throws  GroupNotFoundException
   */
  public static Group findByAlternateName(GrouperSession s, String name, boolean exceptionIfNotFound) 
    throws GroupNotFoundException {
    
    //note, no need for GrouperSession inverse of control
    GrouperSession.validate(s);
    Group g = null;
    g = GrouperDAOFactory.getFactory().getGroup().findByAlternateName(name, exceptionIfNotFound) ;
    
    if (g == null) {
      return g;
    }
    
    //2007-10-16: Gary Brown
    //https://bugs.internet2.edu/jira/browse/GRP-36
    //Ugly... and probably breaks the abstraction but quick and easy to 
    //remove when a more elegant solution found.
    if(s.getSubject().equals(SubjectFinder.findRootSubject()))
      return g;
    
    if ( PrivilegeHelper.canView( s.internal_getRootSession(), g, s.getSubject() ) ) {
      return g;
    }
    LOG.error(E.GF_FBNAME + E.CANNOT_VIEW);
    if (!exceptionIfNotFound) {
      return null;
    }
    throw new GroupNotFoundException(E.GROUP_NOTFOUND + " by alternate name: " + name);
  } 
  
  /** logger */
  private static final Log LOG = GrouperUtil.getLog(GroupFinder.class);

  /**
   * Find all groups within the registry by their {@link GroupType}.  Or empty set if none (never null).
   * <pre class="eg">
   *   Set<Group> groups = GroupFinder.findAllByType( s, GroupTypeFinder.find("your type") );
   * </pre>
   * @param   s     Find group within this session context.
   * @param   type  Find group with this {@link GroupType}.
   * @return  A set of {@link Group}s
   * @throws  IllegalArgumentException
   */
  public static Set<Group> findAllByType(GrouperSession s, GroupType type) throws IllegalArgumentException {
    //note, no need for GrouperSession inverse of control
    NotNullValidator v = NotNullValidator.validate(s);
    if (v.isInvalid()) {
      throw new IllegalArgumentException("null session");
    }
    v = NotNullValidator.validate(type);
    if (v.isInvalid()) {
      throw new IllegalArgumentException("null type");
    }
    Set<Group> groups = PrivilegeHelper.canViewGroups(
      s, GrouperDAOFactory.getFactory().getGroup().findAllByType( type)
    );
    return GrouperUtil.nonNull(groups);
  } 

  /**
   * Find a group within the registry by UUID.
   * <pre class="eg">
   *   Group g = GroupFinder.findByUuid(s, uuid);
   * </pre>
   * @param   s     Find group within this session context.
   * @param   uuid  UUID of group to find.
   * @return  A {@link Group}
   * @throws GroupNotFoundException
   * @Deprecated use the overload
   */
  @Deprecated
  public static Group findByUuid(GrouperSession s, String uuid) throws GroupNotFoundException {
    return findByUuid(s, uuid, true);
  }

  /**
   * Find a group within the registry by UUID.
   * <pre class="eg">
   *   Group g = GroupFinder.findByUuid(s, uuid);
   * </pre>
   * @param   s     Find group within this session context.
   * @param   uuid  UUID of group to find.
   * @param exceptionIfNotFound true if exception if not found
   * @return  A {@link Group}
   * @throws GroupNotFoundException if not found an exceptionIfNotFound is true
   */
  public static Group findByUuid(GrouperSession s, String uuid, boolean exceptionIfNotFound) 
      throws GroupNotFoundException {
    return findByUuid(s, uuid, exceptionIfNotFound, null);
  }
  /**
   * Find a group within the registry by UUID.
   * <pre class="eg">
   *   Group g = GroupFinder.findByUuid(s, uuid);
   * </pre>
   * @param   s     Find group within this session context.
   * @param   uuid  UUID of group to find.
   * @param exceptionIfNotFound true if exception if not found
   * @param queryOptions 
   * @return  A {@link Group}
   * @throws GroupNotFoundException if not found an exceptionIfNotFound is true
   */
  public static Group findByUuid(GrouperSession s, String uuid, boolean exceptionIfNotFound,  QueryOptions queryOptions) 
      throws GroupNotFoundException {
    //note, no need for GrouperSession inverse of control
    GrouperSession.validate(s);
    try {
      Group g = GrouperDAOFactory.getFactory().getGroup().findByUuid(uuid, true, queryOptions);
      if ( PrivilegeHelper.canView( s.internal_getRootSession(), g, s.getSubject() ) ) {
        return g;
      }
    } catch (GroupNotFoundException gnfe) {
      if (exceptionIfNotFound) {
        throw gnfe;
      }
    }
    return null;
  } 

  /**
   * Find a group within the registry by ID index.
   * @param idIndex id index of group to find.
   * @param exceptionIfNotFound true if exception if not found
   * @param queryOptions 
   * @return  A {@link Group}
   * @throws GroupNotFoundException if not found an exceptionIfNotFound is true
   */
  public static Group findByIdIndexSecure(Long idIndex, boolean exceptionIfNotFound,  QueryOptions queryOptions) 
      throws GroupNotFoundException {
    //note, no need for GrouperSession inverse of control
    GrouperSession.validate(GrouperSession.staticGrouperSession());
    Group g = GrouperDAOFactory.getFactory().getGroup().findByIdIndexSecure(idIndex, exceptionIfNotFound, queryOptions);
    return g;
  }

  /**
   * find groups where the static grouper session has certain privileges on the results
   */
  private Set<Privilege> privileges;

  /**
   * if sorting or paging
   */
  private QueryOptions queryOptions;

  /**
   * scope to look for groups Wildcards will be appended or percent is the wildcard
   */
  private String scope;

  /**
   * if the scope has spaces in it, then split by whitespace, and find results that contain all of the scope strings
   */
  private boolean splitScope;

  /**
   * this is the subject that has certain memberships
   */
  private Subject subject;

  /**
   * this is a subject which is not in the group already
   */
  private Subject subjectNotInGroup;
  
  /**
   * assign a subject which does not have a membership in the group
   * @param theSubjectNotInGroup
   * @return this for chaining
   */
  public GroupFinder assignSubjectNotInGroup(Subject theSubjectNotInGroup) {
    this.subjectNotInGroup = theSubjectNotInGroup;
    return this;
  }
  
  /**
   * add a privilege to filter by that the subject has on the group
   * @param privilege should be AccessPrivilege
   * @return this for chaining
   */
  public GroupFinder addPrivilege(Privilege privilege) {
    
    if (this.privileges == null) {
      this.privileges = new HashSet<Privilege>();
    }
    
    this.privileges.add(privilege);
    
    return this;
  }

  /**
   * assign privileges to filter by that the subject has on the group
   * @param theGroups
   * @return this for chaining
   */
  public GroupFinder assignPrivileges(Set<Privilege> theGroups) {
    this.privileges = theGroups;
    return this;
  }

  /**
   * if sorting, paging, caching, etc
   * @param theQueryOptions
   * @return this for chaining
   */
  public GroupFinder assignQueryOptions(QueryOptions theQueryOptions) {
    this.queryOptions = theQueryOptions;
    return this;
  } 

  /**
   * if this is true, or there is a field assigned, then get memberships for a subject
   */
  private boolean membershipsForSubject;

  /**
   * field to look for if searching for memberships in groups
   */
  private Field field;

  /**
   * group ids to find
   */
  private Collection<String> groupIds;

  /**
   * add a group id to search for
   * @param groupId
   * @return this for chaining
   */
  public GroupFinder addGroupId(String groupId) {
    if (this.groupIds == null) {
      this.groupIds = new HashSet<String>();
    }
    this.groupIds.add(groupId);
    return this;
  }

  /**
   * assign group names to search for
   * @param theGroupNames
   * @return this for chaining
   */
  public GroupFinder assignGroupNames(Collection<String> theGroupNames) {
    this.groupNames = theGroupNames;
    return this;
  }
  
  /**
   * group names to find
   */
  private Collection<String> groupNames;

  /**
   * add a group name to search for
   * @param groupName
   * @return this for chaining
   */
  public GroupFinder addGroupName(String groupName) {
    if (this.groupNames == null) {
      this.groupNames = new HashSet<String>();
    }
    this.groupNames.add(groupName);
    return this;
  }

  /**
   * assign group ids to search for
   * @param theGroupIds
   * @return this for chaining
   */
  public GroupFinder assignGroupIds(Collection<String> theGroupIds) {
    this.groupIds = theGroupIds;
    return this;
  }
  
  /**
   * field to look for if searching for memberships in groups
   * @param theField
   * @return this for chaining
   */
  public GroupFinder assignField(Field theField) {
    this.field = theField;
    return this;
  }

  /**
   * field name to look for if searching for memberships in groups
   * @param theFieldName
   * @return theFieldName
   */
  public GroupFinder assignFieldName(String theFieldName) {
    if (StringUtils.isBlank(theFieldName)) {
      this.field = null;
    }
    this.field = FieldFinder.find(theFieldName, true);
    return this;
  }
  
  /**
   * if this is true, or there is a field assigned, then get memberships for a subject
   * @param membershipsForSubject1
   * @return this for chaining
   */
  public GroupFinder assignMembershipsForSubject(boolean membershipsForSubject1) {
    this.membershipsForSubject = membershipsForSubject1;
    return this;
  }
  
  /**
   * find all the group
   * @return the set of groups or the empty set if none found
   */
  public Set<Group> findGroups() {
    GrouperSession grouperSession = GrouperSession.staticGrouperSession();
    
    if (this.membershipsForSubject && this.field == null) {
      this.field = Group.getDefaultList();
    }
    
    Subject privSubject = null;
    
    if (GrouperUtil.length(this.privileges) > 0) {
      privSubject = grouperSession.getSubject();
    }
    
    return GrouperDAOFactory.getFactory().getGroup()
        .getAllGroupsSecure(this.scope, grouperSession, privSubject, this.privileges, 
            this.queryOptions, this.typeOfGroups, this.splitScope, this.subject, 
            this.field, this.parentStemId, this.stemScope, this.findByUuidOrName, 
            this.subjectNotInGroup, this.groupIds, this.groupNames, this.compositeOwner);
    
  }

  /**
   * type of groups to query, if null, qill query just groups and roles and groups that hold people
   */
  private Set<TypeOfGroup> typeOfGroups = new HashSet<TypeOfGroup>();

  /**
   * parent or ancestor stem of the group
   */
  private String parentStemId;

  /**
   * if passing in a stem, this is the stem scope...
   */
  private Scope stemScope;

  /**
   * default constructor
   */
  public GroupFinder() {
    this.typeOfGroups.addAll(TypeOfGroup.GROUP_OR_ROLE_SET);
  }
  
  /**
   * 
   * @param theTypeOfGroups 
   * @return this for chaining
   */
  public GroupFinder assignTypeOfGroups(Set<TypeOfGroup> theTypeOfGroups) {
    
    if (theTypeOfGroups == null) {
      theTypeOfGroups = new HashSet<TypeOfGroup>();
    }
    
    this.typeOfGroups = theTypeOfGroups;
    
    return this;
    
  }
  
  /**
   * 
   * @param typeOfGroup
   * @return this
   */
  public GroupFinder addTypeOfGroup(TypeOfGroup typeOfGroup) {
    
    this.typeOfGroups.add(typeOfGroup);
    return this;
    
  }
  
  /**
   * scope to look for groups  Wildcards will be appended or percent is the wildcard
   * @param theScope
   * @return this for chaining
   */
  public GroupFinder assignScope(String theScope) {
    this.scope = theScope;
    return this;
  }

  /**
   * if the scope has spaces in it, then split by whitespace, and find results that contain all of the scope strings
   * @param theSplitScope
   * @return this for chaining
   */
  public GroupFinder assignSplitScope(boolean theSplitScope) {
    this.splitScope = theSplitScope;
    return this;
  }

  /**
   * this is the subject that has certain memberships in the query
   * @param theSubject
   * @return this for chaining
   */
  public GroupFinder assignSubject(Subject theSubject) {
    this.subject = theSubject;
    return this;
  }

  /**
   * parent or ancestor stem of the group
   * @param theParentStemId
   * @return this for chaining
   */
  public GroupFinder assignParentStemId(String theParentStemId) {
    this.parentStemId = theParentStemId;
    return this;
  }

  /**
   * if passing in a stem, this is the stem scope...
   * @param theStemScope
   * @return this for chaining
   */
  public GroupFinder assignStemScope(Scope theStemScope) {
    this.stemScope = theStemScope;
    return this;
  }

  /**
   * if we are looking up a group, only look by uuid or name
   */
  private boolean findByUuidOrName;
  
  /**
   * if we are looking up a group, only look by uuid or name
   * @param theFindByUuidOrName
   * @return the group finder
   */
  public GroupFinder assignFindByUuidOrName(boolean theFindByUuidOrName) {
    
    this.findByUuidOrName = theFindByUuidOrName;
    
    return this;
  }

  /**
   * find the group
   * @return the group or null
   */
  public Group findGroup() {
    Set<Group> groups = this.findGroups();

    return GrouperUtil.setPopOne(groups);
  }

}


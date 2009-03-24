/*
  Copyright (C) 2007 University Corporation for Advanced Internet Development, Inc.
  Copyright (C) 2007 The University Of Chicago

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

package edu.internet2.middleware.grouper.misc;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;

import edu.internet2.middleware.grouper.cfg.ApiConfig;
import edu.internet2.middleware.grouper.cfg.GrouperConfig;
import edu.internet2.middleware.grouper.internal.dao.AttributeDAO;
import edu.internet2.middleware.grouper.internal.dao.AuditEntryDAO;
import edu.internet2.middleware.grouper.internal.dao.AuditTypeDAO;
import edu.internet2.middleware.grouper.internal.dao.CompositeDAO;
import edu.internet2.middleware.grouper.internal.dao.FieldDAO;
import edu.internet2.middleware.grouper.internal.dao.GroupDAO;
import edu.internet2.middleware.grouper.internal.dao.GroupTypeDAO;
import edu.internet2.middleware.grouper.internal.dao.MemberDAO;
import edu.internet2.middleware.grouper.internal.dao.MembershipDAO;
import edu.internet2.middleware.grouper.internal.dao.RegistryDAO;
import edu.internet2.middleware.grouper.internal.dao.RegistrySubjectDAO;
import edu.internet2.middleware.grouper.internal.dao.StemDAO;
import edu.internet2.middleware.grouper.internal.dao.TransactionDAO;
import edu.internet2.middleware.grouper.internal.util.Realize;
import edu.internet2.middleware.grouper.validator.GrouperValidator;
import edu.internet2.middleware.grouper.validator.NotNullOrEmptyValidator;

/** 
 * Factory for returning <code>GrouperDAO</code> objects.
 * <p/>
 * @author  blair christensen.
 * @version $Id: GrouperDAOFactory.java,v 1.4 2009-03-24 17:12:08 mchyzer Exp $
 * @since   1.2.0
 */
public abstract class GrouperDAOFactory {

  /**
   * 
   */
  private static GrouperDAOFactory gdf;


  /**
   * Return singleton {@link GrouperDAOFactory} implementation.
   * <p/>
   * @return factory
   * @since   1.2.0
   */
  public static GrouperDAOFactory getFactory() {
    if (gdf == null) {
      gdf = getFactory( new ApiConfig() );
    }
    return gdf;
  } 

  /**
   * Return singleton {@link GrouperDAOFactory} implementation using the specified
   * configuration.
   * <p/>
   * @param cfg 
   * @return factory
   * @throws  IllegalArgumentException if <i>cfg</i> is null.
   * @since   1.2.1
   */
  public static GrouperDAOFactory getFactory(ApiConfig cfg) 
    throws  IllegalArgumentException
  {
    if (cfg == null) {
      throw new IllegalArgumentException("null configuration");
    }
    String            klass = cfg.getProperty(GrouperConfig.PROP_DAO_FACTORY);
    GrouperValidator  v     = NotNullOrEmptyValidator.validate(klass);
    if ( v.isInvalid() ) {
      klass = GrouperConfig.DEFAULT_DAO_FACTORY;
    }
    return (GrouperDAOFactory) Realize.instantiate(klass);
  }

  /**
   * @return attribute
   * @since   1.2.0
   */
  public abstract AttributeDAO getAttribute();

  /**
   * @return composite
   * @since   1.2.0
   */
  public abstract CompositeDAO getComposite();

  /**
   * @return audit entry dao
   * @since   1.2.0
   */
  public abstract AuditEntryDAO getAuditEntry();

  /**
   * @return composite
   * @since   1.2.0
   */
  public abstract AuditTypeDAO getAuditType();

  /**
   * @return field
   * @since   1.2.0
   */
  public abstract FieldDAO getField();

  /**
   * @return group dao
   * @since   1.2.0
   */
  public abstract GroupDAO getGroup();

  /**
   * @return group type dao
   * @since   1.2.0
   */
  public abstract GroupTypeDAO getGroupType();

  /**
   * @return member dao
   * @since   1.2.0
   */
  public abstract MemberDAO getMember();

  /**
   * @return membership dao
   * @since   1.2.0
   */
  public abstract MembershipDAO getMembership();

  /**
   * @return registry dao
   * @since   1.2.0
   */
  public abstract RegistryDAO getRegistry();

  /**
   * @return registry subject dao
   * @since   1.2.0
   */
  public abstract RegistrySubjectDAO getRegistrySubject();

  /**
   * @return stem dao
   * @since   1.2.0
   */
  public abstract StemDAO getStem();


  /**
   * 
   */
  public static void internal_resetFactory() {
    gdf = null;
  }

  /**
   * get a hibernate session (note, this is a framework method
   * that should not be called outside of grouper hibernate framework methods
   * @return the session
   */
  public abstract Session getSession();
  
  /**
   * get a hibernate configuration (this is internal for grouper team only)
   * @return the configuration
   */
  public abstract Configuration getConfiguration();
  
  /**
   * return the transaction implementation
   * @since   1.3
   * @return the transaction implementation
   */
  public abstract TransactionDAO getTransaction();


} 


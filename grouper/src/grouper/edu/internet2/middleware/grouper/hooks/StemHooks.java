/*
 * @author mchyzer
 * $Id: StemHooks.java,v 1.1 2008-06-28 06:55:47 mchyzer Exp $
 */
package edu.internet2.middleware.grouper.hooks;

import edu.internet2.middleware.grouper.hooks.beans.HooksContext;
import edu.internet2.middleware.grouper.hooks.beans.HooksStemBean;


/**
 * Extend this class and configure in grouper.properties for hooks on 
 * stem related actions
 */
public abstract class StemHooks {

  //*****  START GENERATED WITH GenerateMethodConstants.java *****//

  /** constant for method name for: stemPostDelete */
  public static final String METHOD_STEM_POST_DELETE = "stemPostDelete";

  /** constant for method name for: stemPostInsert */
  public static final String METHOD_STEM_POST_INSERT = "stemPostInsert";

  /** constant for method name for: stemPostUpdate */
  public static final String METHOD_STEM_POST_UPDATE = "stemPostUpdate";

  /** constant for method name for: stemPreDelete */
  public static final String METHOD_STEM_PRE_DELETE = "stemPreDelete";

  /** constant for method name for: stemPreInsert */
  public static final String METHOD_STEM_PRE_INSERT = "stemPreInsert";

  /** constant for method name for: stemPreUpdate */
  public static final String METHOD_STEM_PRE_UPDATE = "stemPreUpdate";

  //*****  END GENERATED WITH GenerateMethodConstants.java *****//

  /**
   * called right before a stem update
   * @param hooksContext
   * @param preUpdateBean
   */
  public void stemPreUpdate(HooksContext hooksContext, HooksStemBean preUpdateBean) {
    
  }
  
  /**
   * called right after a stem update
   * @param hooksContext
   * @param postUpdateBean
   */
  public void stemPostUpdate(HooksContext hooksContext, HooksStemBean postUpdateBean) {
    
  }
  
  /**
   * called right before a stem insert
   * @param hooksContext
   * @param preInsertBean
   */
  public void stemPreInsert(HooksContext hooksContext, HooksStemBean preInsertBean) {
    
  }
  
  /**
   * called right after a stem insert
   * @param hooksContext
   * @param postInsertBean
   */
  public void stemPostInsert(HooksContext hooksContext, HooksStemBean postInsertBean) {
    
  }
  
  /**
   * called right before a stem delete
   * @param hooksContext
   * @param preDeleteBean
   */
  public void stemPreDelete(HooksContext hooksContext, HooksStemBean preDeleteBean) {
    
  }
  
  /**
   * called right after a stem insert
   * @param hooksContext
   * @param postDeleteBean
   */
  public void stemPostDelete(HooksContext hooksContext, HooksStemBean postDeleteBean) {
    
  }
  
}

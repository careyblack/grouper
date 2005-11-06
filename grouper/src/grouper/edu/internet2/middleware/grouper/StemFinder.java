/*
  Copyright 2004-2005 University Corporation for Advanced Internet Development, Inc.
  Copyright 2004-2005 The University Of Chicago

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

import  java.io.Serializable;
import  net.sf.hibernate.*;

/**
 * Find stems within the Groups Registry.
 * <p />
 * @author  blair christensen.
 * @version $Id: StemFinder.java,v 1.1.2.7 2005-11-06 15:55:19 blair Exp $
 */
public class StemFinder implements Serializable {

  /**
   * Find stem by name.
   * <pre class="eg">
   * // Find the specified stem by name.
   * try {
   *   Stem stem = StemFinder.findByName(s, name);
   * }
   * catch (StemNotFoundException e) {
   *   // Stem not found
   * }
   * </pre>
   * @param   s     Search within this {@link GrouperSession} context
   * @param   name  Find stem with this name.
   * @return  A {@link Stem} object
   * @throws  StemNotFoundException
   */
  public static Stem findByName(GrouperSession s, String name) 
    throws StemNotFoundException
  {
    throw new RuntimeException("Not implemented");
  }

  /**
   * Find stem by uuid.
   * <pre class="eg">
   * // Find the specified stem by uuid.
   * try {
   *   Stem stem = StemFinder.findByUuid(s, uuid);
   * }
   * catch (StemNotFoundException e) {
   *   // Stem not found
   * }
   * </pre>
   * @param   s     Search within this {@link GrouperSession} context
   * @param   uuid  Find stem with this UUID.
   * @return  A {@link Stem} object
   * @throws  StemNotFoundException
   */
  public static Stem findByUuid(GrouperSession s, String uuid) 
    throws StemNotFoundException
  {
    throw new RuntimeException("Not implemented");
  }

  /**
   * Find root stem of the Groups Registry.
   * <pre class="eg">
   * // Find the root stem.
   * Stem rootStem = StemFinder.findRootStem(s);
   * </pre>
   * @param   s     Search within this {@link GrouperSession} context
   * @return  A {@link Stem} object
   * @throws  StemNotFoundException
   */
  public static Stem findRootStem(GrouperSession s) 
    throws StemNotFoundException
  {
    // TODO Should this ever throw a SNFE?
    // TODO This is *obviously* not right
    Stem root = new Stem(s);
    try {
      HibernateHelper.save(root);
      return root;
    }
    catch (HibernateException e) {
      throw new StemNotFoundException(
        "root stem not found: " + e.getMessage()
      );
    }
    //return new Stem();
  } // public static Stem findRootStem(s)

}


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
 * @author mchyzer
 * $Id: AuditControl.java,v 1.1 2009-02-09 05:33:31 mchyzer Exp $
 */
package edu.internet2.middleware.grouper.hibernate;


/**
 *
 */
public enum AuditControl {

  /** will audit this call (or will defer to outside context if auditing */
  WILL_AUDIT, 
  
  /** will not audit */
  WILL_NOT_AUDIT;
  
}

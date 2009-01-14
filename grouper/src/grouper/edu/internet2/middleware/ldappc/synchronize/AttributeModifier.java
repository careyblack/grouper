/*
  Copyright 2006-2007 The University Of Chicago
  Copyright 2006-2007 University Corporation for Advanced Internet Development, Inc.
  Copyright 2006-2007 EDUCAUSE
 
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

package edu.internet2.middleware.ldappc.synchronize;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.DirContext;
import javax.naming.directory.InvalidAttributeValueException;
import javax.naming.directory.ModificationItem;

/**
 * This is a synchronizer helper class for modifying LDAP attribute values. It
 * is to be used in the following manner.
 * <ol>
 * <li>Initialize it with the current values stored in the attribute to be
 * modified. If the current attribute is empty, call {@link #init(Attribute)}
 * passing <code>null</code>. Otherwise call {@link #init(Attribute)} passing
 * an Attribute holding the current set of values. <br>
 * <li>For each value the attribute is to hold, call {@link #store(String)}
 * passing the value. The AttributeModifier determines whether or not the value
 * is an existing value that must remain or is a new value to be added to the
 * attribute.<br>
 * <li>After storing all of the values to be included in the attribute, call
 * {@link #getModifications()} to generate an array of
 * {@link javax.naming.directory.ModificationItem}s that can be used to update
 * the directory. Applying these modifications will result in the attribute
 * holding the desired set of values.
 * </ol>
 * Note that if no values are stored, the modifications generated by
 * AttributeModifier will result in the attribute holding no values. This may
 * not be acceptable for example if the attribute is a <i>MUST</i> attribute.
 * To ensure the generated modifications will always result in the attribute
 * holding a value, the AttributeModifier supports a <i>no value</i>. If the
 * <i>no value</i> is set to a non-null String and no values are stored, the
 * generated modifications will result in attribute holding just the <i>no value</i>.
 * <p>
 * The following assumptions apply when using an AttributeModifier.
 * <ul>
 * <li>An attribute value is to be included exactly once
 * <li>The AttributeModifier is correctly initialized with the current values
 * <li>The attribute in the directory has not been modified prior to applying
 * the generated modifications.
 * </ul>
 * 
 */
public class AttributeModifier
{
    /**
     * Default "no value" value.
     */
    static final String  DEFAULT_NO_VALUE    = null;

    /**
     * Default case sensitivity.
     */
    static final boolean DEFAULT_SENSITIVITY = false;

    /**
     * Name of the attribute.
     */
    private String       attributeName;

    /**
     * Holds additional values to be added to the attribute.
     */
    private Values       adds;

    /**
     * Holds values to be deleted from the attribute.
     */
    private Values       deletes;

    /**
     * Holds values to be retained in the attribute.
     */
    private Values       retained;

    /**
     * Total number of deletes started with.
     * This is automatically initialized to zero.
     */
    private int          deletesCnt;

    /**
     * "no value" value. This is used when the attribute is required, but there
     * are no values to be included.
     */
    private String       noValue;

    /**
     * Indicates if string comparisions are case sensitive.
     */
    private boolean      caseSensitive       = DEFAULT_SENSITIVITY;

    /**
     * Constructs an <code>AttributeModifier</code> for the attribute name.
     * This AttributeModifier is case insensitive and assumes the attribute is
     * not required as the "no value" is not defined.
     * 
     * @param attributeName
     *            Name of the attribute
     */
    public AttributeModifier(String attributeName)
    {
        this(attributeName, DEFAULT_NO_VALUE, DEFAULT_SENSITIVITY);
    }

    /**
     * Constructs an <code>AttributeModifier</code> for the attribute name
     * with the given "no value" value. This AttributeModifier is case
     * insensitive.
     * 
     * @param attributeName
     *            Name of the attribute
     * @param noValue
     *            "no value" value (null if the attribute is not required).
     */
    public AttributeModifier(String attributeName, String noValue)
    {
        this(attributeName, noValue, DEFAULT_SENSITIVITY);
    }

    /**
     * Constructs an <code>AttributeModifier</code> for the attribute name
     * with the case sensitivity for string comparisions set as given. it is
     * assumed that the attribute is not required as the "no value" is not
     * defined.
     * 
     * @param attributeName
     *            Name of the attribute
     * @param caseSensitive
     *            boolean indicating if attribute value comparisions are case
     *            sensitive.
     */
    public AttributeModifier(String attributeName, boolean caseSensitive)
    {
        this(attributeName, DEFAULT_NO_VALUE, caseSensitive);
    }

    /**
     * Constructs an <code>AttributeModifier</code> for the attribute name
     * with the given "no value" value and case sensitivity.
     * 
     * @param attributeName
     *            Name of the attribute
     * @param noValue
     *            "no value" value (null if the attribute is not required).
     * @param caseSensitive
     *            boolean indicating if attribute value comparisions are case
     *            sensitive.
     */
    public AttributeModifier(String attributeName, String noValue, boolean caseSensitive)
    {
        setAttributeName(attributeName);
        setNoValue(noValue);
        setCaseSensitive(caseSensitive);

        adds = new Values();
        deletes = new Values();
        retained = new Values();
    }

    /**
     * Gets the attribute name.
     * 
     * @return Attribute name
     */
    public String getAttributeName()
    {
        return attributeName;
    }

    /**
     * Sets the attribute name.
     * 
     * @param attributeName
     *            the attribute name to set
     */
    public void setAttributeName(String attributeName)
    {
        this.attributeName = attributeName;
    }

    /**
     * Gets the "no value" value. If not <code>null</code>, this value is
     * stored in the attribute when the modifications identified here would
     * result in the attribute having no values.
     * 
     * @return "no value" value
     */
    public String getNoValue()
    {
        return noValue;
    }

    /**
     * Set the "no value" value. If not <code>null</code>, this value is
     * stored in the attribute when the modifications identified here would
     * result in the attribute having no values.
     * 
     * @param noValue
     *            "no value" string, or <code>null</code> if the attribute is
     *            not required to have values.
     */
    public void setNoValue(String noValue)
    {
        this.noValue = noValue;
    }

    /**
     * Indicates whether or not the AttributeModifier is case sensitive when
     * comparing attribute value strings.
     * 
     * @return <code>true</code> if case sensitive, and <code>false</code>
     *         otherwise
     */
    public boolean isCaseSensitive()
    {
        return caseSensitive;
    }

    /**
     * Set the case sensitive flag. This determines if attribute value
     * comparisons are case sensitive or not.
     * 
     * @param caseSensitive
     *            boolean
     */
    private void setCaseSensitive(boolean caseSensitive)
    {
        this.caseSensitive = caseSensitive;
    }

    /**
     * Clears all of the existing values to be added or deleted.
     */
    public void clear()
    {
        adds.clear();
        deletes.clear();
        retained.clear();
        deletesCnt = deletes.size();
    }

    /**
     * Initializes this with an empty value set.
     */
    public void init()
    {
        //
        // Clear any existing values
        //
        clear();

        //
        // Reset deletesCnt based on new deletes
        //
        deletesCnt = deletes.size();
    }

    /**
     * Initializes this with the values from the given attribute. This clears
     * any pre-existing values, and populates it with the given list of values.
     * 
     * @param attribute
     *            Attribute
     * @throws NamingException
     *             thrown if an error occurs accessing the attribute values
     * @throws javax.naming.directory.InvalidAttributeValueException
     *             thrown if a non-String value is encountered
     */
    public void init(Attribute attribute) throws NamingException
    {
        //
        // Clear any existing values
        //
        clear();

        //
        // Populate deletes with current values
        //
        if (attribute != null)
        {
            NamingEnumeration enumeration = attribute.getAll();
            while (enumeration.hasMore())
            {
                //
                // Get the next value
                //
                Object value = enumeration.next();

                //
                // It is assumed that all values are Strings, throw an error if
                // not true
                //
                if (!(value instanceof java.lang.String))
                {
                    throw new InvalidAttributeValueException(attribute.getID() + " has an invalid value of type ["
                            + value.getClass().getName() + "].");
                }
                deletes.add((String) value);
            }
        }

        //
        // Reset deletesCnt based on new deletes
        //
        deletesCnt = deletes.size();
    }

    /**
     * Initializes this with the values from the given collection. This clears
     * any pre-existing values, and populates it with the given list of values.
     * 
     * @param collection
     *            Initial values
     */
    public void init(Collection<String> collection)
    {
        //
        // Clear any existing values
        //
        clear();

        //
        // Populate deletes with current values
        //
        if (collection != null)
        {
            deletes.addAll(collection);
        }

        //
        // Reset deletesCnt based on new deletes
        //
        deletesCnt = deletes.size();
    }

    /**
     * Stores the attribute value. This identifies the value as one that must
     * either remain from the original set or be added to the attribute.
     * 
     * @param attrValue
     *            Attribute value
     * @throws NamingException
     *             thrown if a naming exception occurs
     */
    public void store(String attrValue) throws NamingException
    {
        //
        // If the value is removed from deletes, add it to retained.
        // Else add it to the adds if not already retained or added.
        //
        if (deletes.remove(attrValue))
        {
            retained.add(attrValue);
        }
        else if (!retained.contains(attrValue) && !adds.contains(attrValue))
        {
            adds.add(attrValue);
        }
    }

    /**
     * Returns an attribute with the values to be added. This is based on the
     * state of this object. If there are no values being added and the "no
     * value" value is defined, the "no value" is included. This ignores any
     * existing attribute values.
     * 
     * @return Attribute holding values to be added
     */
    public Attribute getAdditions()
    {
        return makeAttribute(adds);
    }

    /**
     * Returns an array of modification items necessary to update attribute
     * based on the state of this object.
     * 
     * @return A possibly empty array of ModificationItems.
     * @throws NamingException
     *             thrown if a naming error occurs
     */
    public ModificationItem[] getModifications() throws NamingException
    {
        //
        // Init return value to be empty array
        //
        ModificationItem[] mods = new ModificationItem[0];

        //
        // If everything is being deleted, then build a replacement accordingly
        //
        if (deletes.size() == deletesCnt)
        {
            //
            // Init the ModificationItem to be populated
            //
            ModificationItem modItem = null;

            //
            // IF there are additions, replace the current attribute with them
            //
            // ELSE IF there is a "no value", replace the current attribute with
            // it
            //
            // ELSE IF deletes.size() > 0 (i.e., attribute currently holds
            // values), delete the attribute
            //
            // ELSE there is nothing to do
            //
            // (MUST DO THE CHECKS IN THE ORDER GIVEN!)
            //
            if (adds.size() > 0)
            {
                //
                // Replace the current value(s) with the additions
                //
                modItem = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, makeAttribute(adds));
            }
            else if (getNoValue() != null)
            {
                //
                // If deletes only holds the "no value" then nothing to do
                //
                if (deletes.size() == 1 && deletes.contains(getNoValue()))
                {
                    modItem = null;
                }
                else
                {
                    //
                    // Replace the current value(s) with "no value"
                    //
                    modItem = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(getAttributeName(),
                            getNoValue()));
                }
            }
            else if (deletes.size() > 0)
            {
                //
                // Replace the current value(s) with nothing (i.e., delete them)
                //
                modItem = new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute(getAttributeName()));
            }
            else
            {
                //
                // Attribute didn't hold any values to begin with so there is
                // nothing to do
                //
                modItem = null;
            }

            //
            // If there is a modification to be made, reallocate the mods
            // adding the modItem
            //
            if (modItem != null)
            {
                mods = new ModificationItem[] { modItem };
            }
        }
        //
        // Some values are being retained so build add and delete modification
        // items appropriately
        //
        else
        {
            //
            // Create modification array based on changes
            //
            int modsSize = (adds.size() > 0 ? 1 : 0) + (deletes.size() > 0 ? 1 : 0);
            if (modsSize > 0)
            {
                //
                // Re-allocate the mods array
                //
                mods = new ModificationItem[modsSize];

                //
                // Add the modification items
                //
                int modsIndex = 0;

                // Add the deletes
                if (deletes.size() > 0)
                {
                    mods[modsIndex] = new ModificationItem(DirContext.REMOVE_ATTRIBUTE, makeAttribute(deletes));
                    modsIndex++;
                }

                // Add the adds
                if (adds.size() > 0)
                {
                    mods[modsIndex] = new ModificationItem(DirContext.ADD_ATTRIBUTE, makeAttribute(adds));
                    modsIndex++;
                }
            }
        }

        return mods;
    }

    /**
     * Convert an attribute value Map into a BasicAttribute for use with LDAP.
     * 
     * @param attributeSet
     *            the attribute ValueSet to convert.
     * @return a BasicAttribute containing the values in the ValueSet.
     */
    private Attribute makeAttribute(Values attributeSet)
    {
        Attribute attribute = new BasicAttribute(attributeName);
        for (String value : attributeSet)
        {
            attribute.add(value);
        }
        if (getNoValue() != null && attribute.size() == 0)
        {
            attribute.add(getNoValue());
        }
        return attribute;
    }

    /**
     * This method retains all of the current values.
     * 
     * @throws NamingException
     *             thrown if a naming error occurs
     */
    public void retainAll() throws NamingException
    {
        //
        // Add all of the deletes values to retained
        //
        retained.addAll(deletes);

        //
        // Clear deletes of all values
        //
        deletes.clear();
    }

    /**
     * If <tt>caseSensitive</tt> is <tt>true</tt>, return value, otherwise
     * return lowercased value.
     * 
     * Note that caseSensitive is a class variable in the enclosing class.
     * 
     * @param value
     *            string to convert.
     * @return value, possibly lowercased.
     */
    protected String makeComparisonString(String value)
    {
        return caseSensitive ? value : value.toLowerCase();
    }

    /**
     * Implements optional case ignoring set by backing it with a Map, mapping
     * the possibly lowercased values to the actual values.
     */
    public class Values implements Iterable<String>
    {
        /**
         * Serial version UID.
         */
        private static final long   serialVersionUID = 1L;

        /**
         * A backing map for the values, mapping the comparison value to the
         * actual value. If caseSensitive is <tt>true</tt> this is an identity
         * mapping, otherwise it maps the lowercased value to the value itself.
         */
        private Map<String, String> map              = new HashMap<String, String>();

        /**
         * Adds the specified element to the values if it is not already
         * present.
         * 
         * @param value
         *            element to be added to the values.
         * @return <tt>true</tt> if this set did not already contain the
         *         specified element.
         */
        public boolean add(String value)
        {
            boolean addedValue = false;
            String comparisonString = makeComparisonString(value);
            if (!map.containsKey(comparisonString))
            {
                map.put(comparisonString, value);
                addedValue = true;
            }
            return addedValue;
        }

        /**
         * Adds all of the elements in the specified iterable to the values if
         * they're not already present.
         * 
         * @param iterable
         *            iterable whose elements are to be added to the values.
         * @return <tt>true</tt> if the values changed as a result of the
         *         call.
         */
        public boolean addAll(Iterable<String> iterable)
        {
            boolean hasChanged = false;
            for (String value : iterable)
            {
                if (!contains(value))
                {
                    add(value);
                    hasChanged = true;
                }
            }
            return hasChanged;
        }

        /**
         * Removes all of the elements from the values.
         */
        public void clear()
        {
            map.clear();
        }

        /**
         * {@inheritDoc}
         */
        public Iterator<String> iterator()
        {
            return map.values().iterator();
        }

        /**
         * Returns true if the values contain the specified element.
         * 
         * @param object
         *            element whose presence in the values is to be tested.
         * @return true if the values contains the specified element.
         */
        public boolean contains(Object object)
        {
            if (!(object instanceof String))
            {
                return false;
            }

            return map.containsKey(makeComparisonString((String) object));
        }

        /**
         * Removes the specified element from the values if it is present.
         * 
         * @param value
         *            element to be removed from the values, if present.
         * @return true if the value was present in the values.
         */
        public boolean remove(String value)
        {
            return map.remove(makeComparisonString((String) value)) != null;
        }

        /**
         * Returns the number of elements in the values.
         * 
         * @return the number of elements in the values.
         */
        public int size()
        {
            return map.size();
        }
    }
}

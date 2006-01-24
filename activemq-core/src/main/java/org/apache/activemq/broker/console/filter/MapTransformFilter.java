/**
 *
 * Copyright 2005-2006 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.activemq.broker.console.filter;

import org.apache.activemq.broker.console.formatter.GlobalWriter;
import org.apache.activemq.broker.console.AmqMessagesUtil;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQObjectMessage;
import org.apache.activemq.command.ActiveMQBytesMessage;
import org.apache.activemq.command.ActiveMQTextMessage;
import org.apache.activemq.command.ActiveMQMapMessage;
import org.apache.activemq.command.ActiveMQStreamMessage;
import org.apache.activemq.command.ActiveMQMessage;

import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.AttributeList;
import javax.management.Attribute;
import javax.jms.JMSException;
import javax.jms.DeliveryMode;
import java.util.Map;
import java.util.Properties;
import java.util.Iterator;
import java.util.Enumeration;
import java.lang.reflect.Method;

public class MapTransformFilter extends ResultTransformFilter {
    /**
     * Creates a Map transform filter that is able to transform a variety of objects to a properties map object
     * @param next - the next query filter
     */
    public MapTransformFilter(QueryFilter next) {
        super(next);
    }

    /**
     * Transform the given object to a Map object
     * @param object - object to transform
     * @return map object
     */
    protected Object transformElement(Object object) throws Exception {
        // Use reflection to determine how the object should be transformed
        try {
            Method method = this.getClass().getDeclaredMethod("transformToMap", new Class[] {object.getClass()});
            return (Map)method.invoke(this, new Object[] {object});
        } catch (NoSuchMethodException e) {
            GlobalWriter.print("Unable to transform mbean of type: " + object.getClass().getName() + ". No corresponding transformToMap method found.");
            return null;
        }
    }

    /**
     * Transform an ObjectInstance mbean to a Map
     * @param obj - ObjectInstance format of an mbean
     * @return map object
     */
    protected Map transformToMap(ObjectInstance obj) {
        return transformToMap(obj.getObjectName());
    }

    /**
     * Transform an ObjectName mbean to a Map
     * @param objname - ObjectName format of an mbean
     * @return map object
     */
    protected Map transformToMap(ObjectName objname) {
        Properties props = new Properties();

        // Parse object properties
        Map objProps = objname.getKeyPropertyList();
        for (Iterator i=objProps.keySet().iterator(); i.hasNext();) {
            Object key = i.next();
            Object val = objProps.get(key);
            if (val != null) {
                props.setProperty(key.toString(), val.toString());
            }
        }

        return props;
    }

    /**
     * Transform an Attribute List format of an mbean to a Map
     * @param list - AttributeList format of an mbean
     * @return map object
     */
    protected Map transformToMap(AttributeList list) {
        Properties props = new Properties();
        for (Iterator i=list.iterator(); i.hasNext();) {
            Attribute attrib = (Attribute)i.next();

            // If attribute is an ObjectName
            if (attrib.getName().equals(MBeansAttributeQueryFilter.KEY_OBJECT_NAME_ATTRIBUTE)) {
                props.putAll(transformToMap((ObjectName)attrib.getValue()));
            } else {
                if (attrib.getValue() != null) {
                    props.setProperty(attrib.getName(), attrib.getValue().toString());
                }
            }
        }

        return props;
    }

    /**
     * Transform an ActiveMQTextMessage to a Map
     * @param msg - text message to trasnform
     * @return map object
     * @throws JMSException
     */
    protected Map transformToMap(ActiveMQTextMessage msg) throws JMSException {
        Properties props = new Properties();

        props.putAll(transformToMap((ActiveMQMessage)msg));
        if (msg.getText() != null) {
            props.setProperty(AmqMessagesUtil.JMS_MESSAGE_BODY_PREFIX + "JMSText", msg.getText());
        }

        return props;
    }

    /**
     * Transform an ActiveMQBytesMessage to a Map
     * @param msg - bytes message to transform
     * @return map object
     * @throws JMSException
     */
    protected Map transformToMap(ActiveMQBytesMessage msg) throws JMSException {
        Properties props = new Properties();

        props.putAll(transformToMap((ActiveMQMessage)msg));

        long bodyLength = msg.getBodyLength();
        byte[] msgBody;
        int i=0;
        // Create separate bytes messages
        for (i=0; i<(bodyLength/Integer.MAX_VALUE); i++) {
            msgBody = new byte[Integer.MAX_VALUE];
            props.setProperty(AmqMessagesUtil.JMS_MESSAGE_BODY_PREFIX + "JMSBytes:" + (i+1), new String(msgBody));
        }
        msgBody = new byte[(int)(bodyLength % Integer.MAX_VALUE)];
        props.setProperty(AmqMessagesUtil.JMS_MESSAGE_BODY_PREFIX + "JMSBytes:" + (i+1), new String(msgBody));

        return props;
    }

    /**
     * Transform an ActiveMQMessage to a Map
     * @param msg - object message to transform
     * @return map object
     * @throws JMSException
     */
    protected Map transformToMap(ActiveMQObjectMessage msg) throws JMSException {
        Properties props = new Properties();

        props.putAll(transformToMap((ActiveMQMessage)msg));
        if (msg.getObject() != null) {
            // Just add the class name and toString value of the object
            props.setProperty(AmqMessagesUtil.JMS_MESSAGE_BODY_PREFIX + "JMSObjectClass", msg.getObject().getClass().getName());
            props.setProperty(AmqMessagesUtil.JMS_MESSAGE_BODY_PREFIX + "JMSObjectString", msg.getObject().toString());
        }
        return props;
    }

    /**
     * Transform an ActiveMQMapMessage to a Map
     * @param msg - map message to transform
     * @return map object
     * @throws JMSException
     */
    protected Map transformToMap(ActiveMQMapMessage msg) throws JMSException {
        Properties props = new Properties();

        props.putAll(transformToMap((ActiveMQMessage)msg));

        // Get map properties
        Enumeration e = msg.getMapNames();
        while (e.hasMoreElements()) {
            String key = (String)e.nextElement();
            Object val = msg.getObject(key);
            if (val != null) {
                props.setProperty(AmqMessagesUtil.JMS_MESSAGE_BODY_PREFIX + key, val.toString());
            }
        }

        return props;
    }

    /**
     * Transform an ActiveMQStreamMessage to a Map
     * @param msg - stream message to transform
     * @return map object
     * @throws JMSException
     */
    protected Map transformToMap(ActiveMQStreamMessage msg) throws JMSException {
        Properties props = new Properties();

        props.putAll(transformToMap((ActiveMQMessage)msg));
        // Just set the toString of the message as the body of the stream message
        props.setProperty(AmqMessagesUtil.JMS_MESSAGE_BODY_PREFIX + "JMSStreamMessage", msg.toString());

        return props;
    }

    /**
     * Transform an ActiveMQMessage to a Map
     * @param msg - message to transform
     * @return map object
     * @throws JMSException
     */
    protected Map transformToMap(ActiveMQMessage msg) throws JMSException {
        Properties props = new Properties();

        // Get JMS properties
        if (msg.getJMSCorrelationID() != null) {
            props.setProperty(AmqMessagesUtil.JMS_MESSAGE_HEADER_PREFIX + "JMSCorrelationID", msg.getJMSCorrelationID());
        }
        props.setProperty(AmqMessagesUtil.JMS_MESSAGE_HEADER_PREFIX + "JMSDeliveryMode", (msg.getJMSDeliveryMode()==DeliveryMode.PERSISTENT) ? "persistent" : "non-persistent");
        if (msg.getJMSDestination() != null) {
            props.setProperty(AmqMessagesUtil.JMS_MESSAGE_HEADER_PREFIX + "JMSDestination", ((ActiveMQDestination)msg.getJMSDestination()).getPhysicalName());
        }
        props.setProperty(AmqMessagesUtil.JMS_MESSAGE_HEADER_PREFIX + "JMSExpiration", Long.toString(msg.getJMSExpiration()));
        props.setProperty(AmqMessagesUtil.JMS_MESSAGE_HEADER_PREFIX + "JMSMessageID", msg.getJMSMessageID());
        props.setProperty(AmqMessagesUtil.JMS_MESSAGE_HEADER_PREFIX + "JMSPriority", Integer.toString(msg.getJMSPriority()));
        props.setProperty(AmqMessagesUtil.JMS_MESSAGE_HEADER_PREFIX + "JMSRedelivered", Boolean.toString(msg.getJMSRedelivered()));
        if (msg.getJMSReplyTo() != null) {
            props.setProperty(AmqMessagesUtil.JMS_MESSAGE_HEADER_PREFIX + "JMSReplyTo", ((ActiveMQDestination)msg.getJMSReplyTo()).getPhysicalName());
        }
        props.setProperty(AmqMessagesUtil.JMS_MESSAGE_HEADER_PREFIX + "JMSTimestamp", Long.toString(msg.getJMSTimestamp()));
        if (msg.getJMSType() != null) {
            props.setProperty(AmqMessagesUtil.JMS_MESSAGE_HEADER_PREFIX + "JMSType", msg.getJMSType());
        }

        // Get custom properties
        Enumeration e = msg.getPropertyNames();
        while (e.hasMoreElements()) {
            String name = (String)e.nextElement();
            if (msg.getObjectProperty(name) != null) {
                props.setProperty(AmqMessagesUtil.JMS_MESSAGE_CUSTOM_PREFIX + name, msg.getObjectProperty(name).toString());
            }
        }

        return props;
    }
}

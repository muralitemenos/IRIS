package com.temenos.interaction.media.odata.xml.atom;

/*
 * #%L
 * interaction-media-odata-xml
 * %%
 * Copyright (C) 2012 - 2013 Temenos Holdings N.V.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */


import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.odata4j.core.ODataConstants;
import org.odata4j.core.OEntity;
import org.odata4j.edm.EdmEntitySet;
import org.odata4j.format.FormatWriter;
import org.odata4j.format.xml.XmlFormatWriter;
import org.odata4j.internal.InternalUtil;
import org.odata4j.producer.EntitiesResponse;
import org.odata4j.stax2.QName2;
import org.odata4j.stax2.XMLFactoryProvider2;
import org.odata4j.stax2.XMLWriter2;

import com.temenos.interaction.core.hypermedia.Link;
import com.temenos.interaction.core.hypermedia.ResourceState;

public class AtomFeedFormatWriter extends XmlFormatWriter implements FormatWriter<EntitiesResponse> {
	
	private ResourceState serviceDocument;
	private AtomEntryFormatWriter entryWriter;
	
	public AtomFeedFormatWriter(ResourceState serviceDocument) {
		this.serviceDocument = serviceDocument;
		entryWriter = new AtomEntryFormatWriter(serviceDocument);
	}
	
  @Override
  public String getContentType() {
    return ODataConstants.APPLICATION_ATOM_XML_CHARSET_UTF8;
  }

  @Override
  public void write(UriInfo uriInfo, Writer w, EntitiesResponse response) {
	  EdmEntitySet ees = response.getEntitySet();
	  String entitySetName = ees.getName();
	  List<Link> links = new ArrayList<Link>();
	  links.add(new Link(entitySetName, "self", entitySetName, null, null));
	  write(uriInfo, w, links, response, null, null, null);
  }
  
  public void write(UriInfo uriInfo, Writer w, Collection<Link> links, EntitiesResponse response, String modelName, Map<OEntity, Collection<Link>> linkId,Map<String,String> hiddenColumnValue) {
	String baseUri = AtomXMLProvider.getBaseUri(serviceDocument, uriInfo);
	String absolutePath = AtomXMLProvider.getAbsolutePath(uriInfo);

    EdmEntitySet ees = response.getEntitySet();
    String entitySetName = ees.getName();
    DateTime utc = new DateTime().withZone(DateTimeZone.UTC);
    String updated = InternalUtil.toString(utc);

    XMLWriter2 writer = XMLFactoryProvider2.getInstance().newXMLWriterFactory2().createXMLWriter(w);
    writer.startDocument();

    writer.startElement(new QName2("feed"), atom);
    writer.writeNamespace("m", m);
    writer.writeNamespace("d", d);
    writer.writeAttribute("xml:base", baseUri);

    writeElement(writer, "title", entitySetName, "type", "text");
    writeElement(writer, "id", absolutePath);

    writeElement(writer, "updated", updated);

    assert(links != null);
    for (Link link : links) {
    	// href is relative path from base path
    	String href = link.getRelativeHref(baseUri);
    	String title = link.getTitle();
		String rel = link.getRel();
        writeElement(writer, "link", null, "rel", rel, "title", title, "href", href);
    }

    Integer inlineCount = response.getInlineCount();
    if (inlineCount != null) {
      writeElement(writer, "m:count", inlineCount.toString());
    }
    
    if(hiddenColumnValue!=null){
        if (!hiddenColumnValue.isEmpty()) {
            writer.startElement("m:dynamicAttributes");
            for (String key : hiddenColumnValue.keySet()) {
                writer.startElement("m:dynamicAttribute");
                writeElement(writer, "d:columnName", key);
                writeElement(writer, "d:value", hiddenColumnValue.get(key));
                writer.endElement("m:dynamicAttribute");
            }
            writer.endElement("m:dynamicAttributes");
        }
    }

    for (OEntity entity : response.getEntities()) {
    	Collection<Link> link =  linkId.get(entity);
    	writer.startElement("entry");
    	
    	String etag = entity.getEntityTag();
    	
    	if(StringUtils.isNotEmpty(etag)) {
    	    writer.writeAttribute("m:etag", etag);
    	}
    	
    	entryWriter.writeEntry(writer, entity, entity.getProperties(), entity.getLinks(), baseUri, updated, ees, true, link);
    	writer.endElement("entry");
    }

    if (response.getSkipToken() != null) {
      //<link rel="next" href="https://odata.sqlazurelabs.com/OData.svc/v0.1/rp1uiewita/StackOverflow/Tags/?$filter=TagName%20gt%20'a'&amp;$skiptoken=52" />
      String nextHref = uriInfo.getRequestUriBuilder().replaceQueryParam("$skiptoken", response.getSkipToken()).build().toString();
      writeElement(writer, "link", null, "rel", "next", "href", nextHref);
    }

    writer.endDocument();

  }

}

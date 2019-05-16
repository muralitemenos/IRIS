package com.temenos.interaction.commands.solr;

/*
 * The SOLR search command. Can be called with the following parameters.
 * 
 *      'core'      Name of the core to search. Defaults to the entity1 core.
 *      'q'         SOLR query term to search for.
 *      'feldname'  Name of field to search. Defaults to 'text' i.e. all fields (See schema.xml for details).
 * 
 */

/*
 * #%L
 * interaction-commands-solr
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response.Status;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServer;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrServer;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.temenos.interaction.authorization.command.AuthorizationAttributes;
import com.temenos.interaction.commands.solr.data.SolrConstants;
import com.temenos.interaction.core.command.InteractionCommand;
import com.temenos.interaction.core.command.InteractionContext;
import com.temenos.interaction.core.command.InteractionException;
import com.temenos.interaction.core.entity.EntityProperties;
import com.temenos.interaction.odataext.odataparser.ODataParser;
import com.temenos.interaction.odataext.odataparser.data.FieldName;
import com.temenos.interaction.odataext.odataparser.data.RowFilter;

public class SolrSearchCommand extends AbstractSolrCommand implements InteractionCommand {

	private final static Logger logger = LoggerFactory.getLogger(SolrSearchCommand.class);

	private static final String TEXT = "text";
	private static final String COLON = ":";
	private static final String STAR = "*";

	/**
	 * Instantiates a new select command.
	 * 
	 * For production we pass in, and connect to, the URL of an external server
	 * for each search request.
	 * 
	 * In future we may introduce connection pooling.
	 */
	public SolrSearchCommand(String solrRootURL) {
		this.solrRootURL = solrRootURL;
	}

	protected SolrSearchCommand() {}

	@Override
	public Result execute(InteractionContext ctx) throws InteractionException {
		try {
			URL coreURL = new URL(solrRootURL + "/" +  getCoreName(ctx));
			// URL coreURL = new URL(solrRootURL + "/" + coreName);
			logger.info("Connecting to external Solr server " + coreURL + ".");
			return execute(ctx, new HttpSolrServer(coreURL.toString()));
		} catch (MalformedURLException e) {
			logger.error("Malformed URL when connecting to Solr Server. " + e);
			throw new InteractionException(Status.BAD_REQUEST, "Malformed URL when connecting to Solr Server", e);
		}
	}

	protected Result execute(InteractionContext ctx, SolrServer solrServer) throws InteractionException {
		logQueryParameters(ctx.getQueryParameters());

		// Set up query
		SolrQuery query = buildQuery(ctx.getQueryParameters());
		if (null == query) {
			// Could not build a valid query.
			throw new InteractionException(Status.BAD_REQUEST, "Search query is empty, please provide valid options");
		}

		// TODO The following 4 lines, and the URL built with 'comapnyName', are a temporary work round to RTC1671119.
		// TODO Once expected behavior is understood feel free to remove this.

		// Run the query
		Result res = Result.FAILURE;
		try {
		    QueryResponse rsp = solrServer.query(query);
			// SolrDocumentList list = rsp.getResults();

			ctx.setResource(buildCollectionResource(getEntityName(ctx), rsp.getResults()));
	
			// Indicate that database level filtering was successful.
			ctx.setAttribute(AuthorizationAttributes.FILTER_DONE_ATTRIBUTE, Boolean.TRUE);
			ctx.setAttribute(AuthorizationAttributes.SELECT_DONE_ATTRIBUTE, Boolean.TRUE);
			
			res = Result.SUCCESS;
		} catch (SolrException e) {
			logger.error("An unexpected internal error occurred while querying Solr " + e);
		} catch (SolrServerException e) {
			logger.error("An unexpected error occurred while querying Solr " + e);
		}

		solrServer.shutdown();

		return res;
	}

	private void logQueryParameters(MultivaluedMap<String, String> queryParams) {
		Iterator<String> it = queryParams.keySet().iterator();
		logger.info("SolrSearch command parameters:");
		while (it.hasNext()) {
			String theKey = (String) it.next();
			logger.info("    " + theKey + " = " + queryParams.getFirst(theKey));
		}
	}

    private String getCoreName(InteractionContext ctx) throws InteractionException {
        String companyName = getCompanyId(ctx);
        if (ctx.getQueryParameters().containsKey(SolrConstants.SOLR_CORE_KEY)) {
            return ctx.getQueryParameters().getFirst(SolrConstants.SOLR_CORE_KEY);
        }
        if ((companyName != null) && (!companyName.isEmpty())) {
            return companyName + "_" + getEntityName(ctx);
        }
        return getEntityName(ctx);
    }

	private String getCompanyId(InteractionContext ctx) throws InteractionException {
		String companyName = ctx.getPathParameters().getFirst("companyid");
		if (null == companyName) {
			throw new InteractionException(Status.BAD_REQUEST, "Missing company id");
		}
		return companyName;
	}

	private String getEntityName(InteractionContext ctx) throws InteractionException {
		String entityName = ctx.getCurrentState().getEntityName();
		if (entityName == null || entityName.isEmpty()) {
			throw new InteractionException(Status.INTERNAL_SERVER_ERROR, "Missing entity name");
		}
		return entityName;
	}

	private SolrQuery buildQuery(MultivaluedMap<String, String> queryParams) {
		SolrQuery query = new SolrQuery();

		// Add Number of rows to fetch
		addNumOfRows(query, queryParams);
		
		// Add Shards for Distributed Query support
		addShards(query, queryParams);
		
		// Add the query string
		String queryString = buildQueryString(queryParams);
		if (null != queryString) {
			query.setQuery(queryString);
		}

		// Add the filter string (like query but does hard matching).
		addFilter(query, queryParams);

		// If returned fields have been limited by authorization set them
		addSelect(query, queryParams);

		return (query);
	}

	/**
	 * By default SolrQuery only returns 10 rows. This is true even if more
     * rows are available. This method will check if user has provided its preference
     * using $top, otherwise use Solr Default
	 * @param query
	 * @param queryParams
	 */
	private void addNumOfRows(SolrQuery query, MultivaluedMap<String, String> queryParams) {
	    int top = 0;
	   try {
	       String topStr = queryParams.getFirst("$top");
	       top = topStr == null || topStr.isEmpty() ? 0 : Integer.parseInt(topStr);
	   } catch (NumberFormatException nfe) {
	       // Do nothing and ignore as we have default value to use

	   }
	   if (top > 0) {
           query.setRows(top);
       } else {
           query.setRows(MAX_ENTITIES_RETURNED);
       }
    }

	/**
	 * This method will add Shards to the Query
	 * @param query
	 * @param queryParams
	 */
	private void addShards(SolrQuery query, MultivaluedMap<String, String> queryParams) {
	    String shards = queryParams.getFirst(SolrConstants.SOLR_SHARDS_KEY);
	    if (shards != null && !shards.isEmpty()) {
	        query.setParam(SolrConstants.SOLR_SHARDS_KEY, shards);
	        // Check if user has specified shards.tolerant, add if available
	        String shardsTolerant = queryParams.getFirst(SolrConstants.SOLR_SHARDS_TOLERANT_KEY);
	        if (shardsTolerant != null && !shardsTolerant.isEmpty()) {
	            query.setParam(SolrConstants.SOLR_SHARDS_TOLERANT_KEY, shardsTolerant);
	        }
	    }
	}
	
	// Build Solr field list from an OData $select option.
	private void addSelect(SolrQuery query, MultivaluedMap<String, String> queryParams) {

		// If we were passed an OData $select parse it and add to the query
		String selectOption = queryParams.getFirst(ODataParser.SELECT_KEY);
		if (null != selectOption) {
			// Its a comma separated list of fields.
			Set<FieldName> fields = ODataParser.parseSelect(selectOption);

			logger.info("Adding selects:");
			for (FieldName field : fields) {
				logger.info("    " + field.getName());
				query.addField(field.getName());
			}
		}
		return;
	}

	// Build the Solr query string from passed request.
	private String buildQueryString(MultivaluedMap<String, String> queryParams) {
		String query = queryParams.getFirst(SolrConstants.SOLR_QUERY_KEY);
		if (null == query || query.isEmpty()) {
			return TEXT + COLON + STAR;
		}
		query = query.trim();
		if (!query.contains(COLON)) {
			return TEXT + COLON + query;
		}
		while (query.startsWith(STAR)) {
			query = query.substring(1, query.length());
		}
		if (query.startsWith(COLON)) {
			return TEXT + query;
		}
		return query;
	}

	// Build the Solr query string from passed request and any authorization
	// restrictions.
	private void addFilter(SolrQuery query, MultivaluedMap<String, String> queryParams) {

		// If we were passed an OData $filter parse it and add to the query
		String filterOption = queryParams.getFirst(ODataParser.FILTER_KEY);
		if (null != filterOption) {
			try {
				List<RowFilter> filters = ODataParser.parseFilter(filterOption);

				logger.info("Adding filters:");
				for (RowFilter filter : filters) {
					logger.info("    " + filter.getFieldName().getName() + " " + filter.getRelation().getoDataString()
							+ " " + filter.getValue());
					
					// Build filter query. Filter query (fq) syntax is non obvious. Check out on line references.

					switch (filter.getRelation()) {
					case EQ:
					    if(filter.getValue().contains("...")){
                            /* Special case - forming filter query for LK operand
                             * ex1 - '...xxx...' -> *xxx*
                             * ex2 - 'xxx...' -> xxx*
                             * ex3 - '...xxx' -> *xxx
                             */
                            String filterQuery = getFilterQueryForLK(filter);
                            if (filterQuery != null) {
                                query.addFilterQuery(filterQuery);
                            }
                        }else{
                            /* Removing quotes from filter value, if exist and solr understand the field name and value using ":". so replacing ":" to "\:"
                             * ex - '2019-02-13T05:21:56Z' -> 2019-02-13T05\:21\:56Z
                             */
                        query.addFilterQuery(filter.getFieldName().getName() + ":\"" + filter.getValue().replace("'", "").replace(":", "\\:") + "\"");
                        }
						break;

					case NE:
					    if(filter.getValue().contains("...")){
                            String filterQuery = getFilterQueryForUL(filter);
                            if (filterQuery != null) {
                                query.addFilterQuery(filterQuery);
                            }
                        }else{
                        query.addFilterQuery("-" + filter.getFieldName().getName() + ":\"" + filter.getValue().replace("'", "").replace(":", "\\:") + "\"");
                        }
						break;

					case LT:
						// fq comparisons uses 'inclusive' [x TO y] syntax. To get an 'exclusive' lt use 'not gt'.
					    query.addFilterQuery(filter.getFieldName().getName() + ":{* TO \"" + filter.getValue().replace("'", "").replace(":", "\\:") + "\"}");
						break;

					case GT:
						// fq comparisons uses 'inclusive' [x TO y] syntax. To get an 'exclusive' gt use 'not lt'.
					    query.addFilterQuery(filter.getFieldName().getName() + ":{\"" + filter.getValue().replace("'", "").replace(":", "\\:") + "\" TO *}");
						break;
						
					case LE:
					    query.addFilterQuery(filter.getFieldName().getName() + ":[* TO \"" + filter.getValue().replace("'", "").replace(":", "\\:") + "\"]");
						break;

					case GE:
					    query.addFilterQuery(filter.getFieldName().getName() + ":[\"" + filter.getValue().replace("'", "").replace(":", "\\:") + "\" TO *]");
						break;

					default:
						logger.warn("Filter condition \"" + filter.getRelation()
								+ "\" not yet implemented ... ignored.");
					}
				}
			} catch (ODataParser.UnsupportedQueryOperationException e) {
				logger.error("Could not interpret OData " + ODataParser.FILTER_KEY + " = " + filterOption, e);
				return;
			}
		}
		return;
	}
	
	/**
     * @param filter
     * @return
     */
    private String getFilterQueryForUL(RowFilter filter) {
        String filterQuery = null;
        String filterValue = filter.getValue();
        if (filterValue.startsWith("'...") && filterValue.endsWith("...'")) {
            /*
             * Forming solr equivalent filter for UL with contains. 
             * Ex - Mnemonic ul ...xxx... converted in to !Mnemonic:*xxx*
             */
            filterValue = filterValue.substring(filterValue.indexOf(".") + 3, filterValue.lastIndexOf(".") - 2).trim();
            filterQuery = "!" + filter.getFieldName().getName() + ":*" + filterValue + "*";
        } else if (!filterValue.startsWith("'...") && filterValue.endsWith("...'")) {
            /*
             * Forming solr equivalent filter for UL with startswith. 
             * Ex - Mnemonic ul xxx... converted in to !Mnemonic:xxx*
             */
            filterValue = filterValue.substring(filterValue.indexOf("'") + 1, filterValue.indexOf(".")).trim();
            filterQuery = "!" + filter.getFieldName().getName() + ":" + filterValue + "*";
        } else if (filterValue.startsWith("'...") && !filterValue.endsWith("...'")) {
            /*
             * Forming solr equivalent filter for UL with endswith. 
             * Ex - Mnemonic ul ...xxx converted in to !Mnemonic:*xxx
             */
            filterValue = filterValue.substring(filterValue.indexOf(".") + 3, filterValue.lastIndexOf("'")).trim();
            filterQuery = "!" + filter.getFieldName().getName() + ":*" + filterValue;
        }
        return filterQuery;
    }

     /**
     * @param filter
     * @return
     */
    private String getFilterQueryForLK(RowFilter filter) {
        String filterQuery = null;
        String filterValue = filter.getValue();
        if (filterValue.startsWith("'...") && filterValue.endsWith("...'")) {
            /*
             * Forming solr equivalent filter for LK with contains. 
             * Ex - Mnemonic lk ...xxx... converted in to Mnemonic:*xxx*
             */ 
            filterValue = filterValue.substring(filterValue.indexOf(".") + 3, filterValue.lastIndexOf(".") - 2).trim();
            filterQuery = filter.getFieldName().getName() + ":*" + filterValue + "*";
        } else if (!filterValue.startsWith("'...") && filterValue.endsWith("...'")) {
            /*
             * Forming solr equivalent filter for LK with startswith. 
             * Ex - Mnemonic lk xxx... converted in to Mnemonic:xxx*
             */
            filterValue = filterValue.substring(filterValue.indexOf("'") + 1,filterValue.indexOf(".")).trim();
            filterQuery = filter.getFieldName().getName() + ":" + filterValue + "*";
        } else if (filterValue.startsWith("'...") && !filterValue.endsWith("...'")) {
            /*
             * Forming solr equivalent filter for LK with endswith. 
             * Ex - Mnemonic lk ...xxx converted in to Mnemonic:*xxx
             */
            filterValue = filterValue.substring(filterValue.indexOf(".") + 3, filterValue.lastIndexOf("'")).trim();
            filterQuery = filter.getFieldName().getName() + ":*" + filterValue;
        }
        return filterQuery;
    }
	
	@Override
	protected void customizeEntityProperties(SolrDocument doc, EntityProperties properties) {
	    // By default nothing needs to be done
	}
}

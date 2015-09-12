package net.laurenwolfe

//import grails.converters.JSON
import groovy.json.JsonSlurper;
import groovy.json.JsonOutput;

class QueryController {
    //Specify scope-- otherwise, the class vars save state between requests (adding data on top of old requests)
    static scope = "request"

    //Paths to rexster REST API
    def rexsterURL = "http://glados49:8182"
    def gremlin = "/graphs/tumorgraph/tp/gremlin?script="
    def rexster = "/graphs/tumorgraph/"
    def edges = []
    def nodes = []

    //This is hit by jQuery AJAX call, which passes query parameter via URL
    def queryGremlin() {
        def queryStr = params.query;

        if(queryStr) {
            //get JSON response from Rexster based on query
            def queryResult = new JsonSlurper().parseText(new URL(rexsterURL + gremlin + queryStr).text)

            /*To determine which path we go down, we get size and type of result.
             * One vertex means we get that vertex and all of its adjacent vertices and edges.
             * Multiple vertices means we take those vertices and look for any edges between them.
             * One or more edges means we take those edges and build out their vertices.
             */
            def count = queryResult.results.size()
            def type = queryResult.results[0]._type

            if (count == 1 && type == "vertex") {
                def id = queryResult.results[0]._id
                def alchemyVertex = makeVertex(queryResult.results[0])
                nodes.add(alchemyVertex)
                getVerticesById(id)
                getEdgesById(id)
                joinNodesAndEdges()
            } else if (type == "vertex") {
                def vertexIds = addVertices(queryResult.results)
                findEdges(vertexIds)
                joinNodesAndEdges()
            } else if (type == "edge") {
                def vertexIds = addEdges(queryResult.results)
                addVerticesFromList(vertexIds)
                joinNodesAndEdges()
            } else {
                render "Error, invalid query!"
            }
        } else {
            render "Error, no query provided!"
        }
    }

    /***********************************************
     * Functions for query returning SINGLE VERTEX
     *
     **********************************************/

    //Provided with a vertex id, get all of its adjacent vertices and add to nodes list.
    def getVerticesById(id) {
        def queryStr = "vertices/" + id + "/both"

        def queryResult = new JsonSlurper().parseText( new URL( rexsterURL + rexster + queryStr ).text )

        //translate results to alchemy format
        queryResult.results.each{ vertex ->
            makeVertex(vertex)
        }
    }

    //Provided with a vertex id, get all of its incoming and outgoing edges.
    def getEdgesById(id) {
        def queryStr = "vertices/" + id + "/bothE"

        def queryResult = new JsonSlurper().parseText( new URL( rexsterURL + rexster + queryStr ).text )

        //translate results to alchemy format
        queryResult.results.each{ edge ->
            makeEdge(edge)
        }
    }

    /***************************************************
     * Functions for query returning MULTIPLE VERTICES
     *
     **************************************************/

    //Take vertices and convert to alchemy format
    def addVertices(vertices) {
        def vertexIds = []

        vertices.each{ vertex ->
            vertexIds.add(vertex._id)
            makeVertex(vertex)
        }

        return vertexIds
    }

    //Pass a list of vertex ids, finds any edges between them, makes them
    def findEdges(ids) {
        ids.unique()

        //Pop off the first id so that we can check it against those remaining in the list
        def currId = ids.removeAt(0)

        //Loop over all node id combinations, looking for edges
        while(ids.size() > 0) {
            for(def i = 0; i < ids.size(); i++) {

                def boolQuery = "g.v(" + currId.toString() + ").both.retain([g.v(" + ids.get(i).toString() + ")]).hasNext()"

                //Call to Rexster REST API
                def hasEdge = new JsonSlurper().parseText( new URL( rexsterURL + gremlin + boolQuery ).text )

                //If an edge is found, push it into edges list
                if(hasEdge.results[0]) {
                    def edgeQuery = "g.v(" + currId.toString()  + ").bothE.as('x').bothV.retain([g.v(" +
                            ids.get(i).toString()  + ")]).back('x')"

                    def edge = new JsonSlurper().parseText( new URL( rexsterURL + gremlin + edgeQuery ).text )

                    makeEdge(edge.results[0])
                }
            }
            currId = ids.removeAt(0)
        }
    }


    /****************************************
     * Functions for query returning EDGE(S)
     *
     ***************************************/

    //given edges, make edges and collect vertex ids
    def addEdges(edges) {
        def vertexIds = []

        edges.each{ edge ->
            vertexIds.add(edge._inV)
            vertexIds.add(edge._outV)
            makeEdge(edge)
        }

        vertexIds.unique()

        return vertexIds
    }

    //make edges based on collected vertex ids
    def addVerticesFromList(vertexIds) {
        def queryStr

        vertexIds.each{ vertexId ->
            queryStr = rexsterURL + rexster + "vertices/" + vertexId.toString()

            def queryResult = new JsonSlurper().parseText( new URL( rexsterURL + rexster + queryStr ).text )

            makeVertex(queryResult.results)
        }
    }

    /******************
     * HELPER METHODS
     *
     *****************/

    //Build vertex in alchemy format
    def makeVertex(vertex) {

        def alchemyNode = [
            id: vertex._id,
            objectID: vertex.objectID,
            name: vertex.name,
            chr: vertex.chr,
            start: vertex.start,
            end: vertex.end,
            strand: vertex.strand,
            tumor_type: vertex.tumor_type,
            version: vertex.version,
            feature_type: vertex.feature_type,
            annotation: vertex.annotation
        ];

        nodes.add(alchemyNode)

    }

    //Build edge in alchemy format
    def makeEdge(edge) {
        def alchemyEdge = [
                source: edge._inV,
                target: edge._outV,
                edgeID: edge._id,
                correlation: edge.correlation,
                sample_size: edge.sample_size,
                min_log_p_uncorrected: edge.min_log_p_uncorrected,
                bonferroni: edge.bonferroni,
                min_log_p_corrected: edge.min_log_p_corrected,
                excluded_sample_count_a: edge.excluded_sample_count_a,
                min_log_p_unused_a: edge.min_log_p_unused_a,
                excluded_sample_count_b: edge.excluded_sample_count_b,
                min_log_p_unused_b: edge.min_log_p_unused_b,
                genomic_distance: edge.genomic_distance,
                feature_types: edge.feature_types
        ]

        edges.add(alchemyEdge)
    }

    //Build the list of all nodes and edges in alchemy format and convert to graphJSON
    def joinNodesAndEdges() {
        def alchemyList = [nodes: nodes, edges: edges]

        def json = JsonOutput.toJson(alchemyList)

        render json
    }
}

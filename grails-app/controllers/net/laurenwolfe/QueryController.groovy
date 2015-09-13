package net.laurenwolfe

//import grails.converters.JSON
import groovy.json.JsonSlurper;
import groovy.json.JsonOutput;
import groovy.time.*

class QueryController {
    //Specify scope-- otherwise, the class vars save state between requests (adding data on top of old requests)
    static scope = "request"

    //benchmarking
    def timeStart = new Date()

    //Paths to rexster REST API
//    def rexsterURL = "192.168.99.100:8182"
    def rexsterURL = "http://glados49:8182"
    def gremlin = "/graphs/tumorgraph/tp/gremlin?script="
    def rexster = "/graphs/tumorgraph/"
    def edges = []
    def nodes = []


    def index() {
        def queryStr = params.query

        queryGremlin(queryStr)
    }

    def newRoot() {
        def id = params.id
        getVertexById(id)
        getVerticesById(id)
        getEdgesById(id)
        joinNodesAndEdges()
    }

    def getVertexById(id) {
        def queryStr = "vertices/" + id

        def queryResult = new JsonSlurper().parseText( new URL( rexsterURL + rexster + queryStr ).text )
        makeVertex(queryResult.results[0])
    }

    //This is hit by jQuery AJAX call, which passes query parameter via URL
    def queryGremlin(queryStr) {

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
                makeVertex(queryResult.results[0])
                getVerticesById(id)
                getEdgesById(id)
                joinNodesAndEdges()
            } else if (type == "vertex") {
                def vertexIds = addVertices(queryResult.results)
                compileEdges(vertexIds)
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

    def compileEdges(vIds) {
        def tempEdges = []

        vIds.unique()

        for(def i = 0; i < vIds.size; i++) {
            def adjEdgeQuery = "vertices/" + vIds[i].toString() + "/bothE"

            def edges = new JsonSlurper().parseText( new URL( rexsterURL + rexster + adjEdgeQuery ).text )

            tempEdges.addAll(edges.results)
        }

        sortAndFilterEdges(tempEdges)
    }

    def sortAndFilterEdges(tempEdges) {
        //Sort edge list

        tempEdges.sort { a, b ->
            a._id <=> b._id
        }

        //compare adjacent (sorted) edges. If match is found and this edge id hasn't already been added, parse edge. Increment counter.
        for(def i = 0; i < tempEdges.size; i++) {
            if(tempEdges[i].equals(tempEdges[i+1])) {
                //parse edge
                makeEdge(tempEdges[i])
                //move both of these out of the queue
                i = i + 2
            } else {
                i++
            }
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
            queryStr = "vertices/" + vertexId.toString()

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

        //Benchmarking
        def timeStop = new Date()
        TimeDuration duration = TimeCategory.minus(timeStop, timeStart)
        def dur = "runtime: " + duration.toString()

        def alchemyList = [comment: dur, nodes: nodes, edges: edges]

        def json = JsonOutput.toJson(alchemyList)

        render json
    }
}

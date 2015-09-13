package net.laurenwolfe


class QueryInterceptor {

    boolean before() {
        header( "Access-Control-Allow-Origin", "*" )
        header( "Access-Control-Allow-Methods", "POST, GET, OPTIONS, DELETE" )
        header( "Access-Control-Max-Age", "360000" )
        header("Access-Control-Allow-Headers", "Content-Type")
        true
    }

    boolean after() { true }

    void afterView() {
        // no-op
    }
}

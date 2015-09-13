package net.laurenwolfe


import grails.test.mixin.TestFor
import spock.lang.Specification

/**
 * See the API for {@link grails.test.mixin.web.ControllerUnitTestMixin} for usage instructions
 */
@TestFor(QueryInterceptor)
class QueryInterceptorSpec extends Specification {

    def setup() {
    }

    def cleanup() {

    }

    void "Test query interceptor matching"() {
        when:"A request matches the interceptor"
            withRequest(controller:"query")

        then:"The interceptor does match"
            interceptor.doesMatch()
    }
}

package other

import spock.lang.Specification

class HelloSpec extends Specification {

    void 'assert hello'() {
        expect:
            Hello.hello() == 'Hello'
    }

}

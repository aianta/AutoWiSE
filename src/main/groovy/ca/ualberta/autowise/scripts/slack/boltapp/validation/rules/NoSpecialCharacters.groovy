package ca.ualberta.autowise.scripts.slack.boltapp.validation.rules


import java.util.function.Predicate
import java.util.regex.Pattern


/**
 * Ensure text is alphanumeric and spaces.
 */
class NoSpecialCharacters implements Predicate<String> {

    private Pattern allowedRegex = Pattern.compile("[A-Za-z0-9 ]*")

    @Override
    boolean test(String s) {
        return s.matches(allowedRegex)
    }
}

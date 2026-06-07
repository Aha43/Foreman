package foreman.app;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProjectRegistrationServiceTest {

    @Test
    void stripsChatSuffix() {
        assertEquals("Dev", ProjectRegistrationService.displayLabel("Dev Chat"));
    }

    @Test
    void stripsTrailingChatCaseInsensitive() {
        assertEquals("Dev", ProjectRegistrationService.displayLabel("Dev CHAT"));
    }

    @Test
    void preservesNameWithoutChatSuffix() {
        assertEquals("Planner", ProjectRegistrationService.displayLabel("Planner"));
    }

    @Test
    void doesNotStripChatInMiddleOfName() {
        assertEquals("ChatBot", ProjectRegistrationService.displayLabel("ChatBot"));
    }
}

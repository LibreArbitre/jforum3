package net.jforum;

import junit.framework.TestCase;
import net.jforum.context.ForumContext;
import net.jforum.context.RequestContext;
import net.jforum.context.ResponseContext;

public class JForumExecutionContextTest extends TestCase
{
    private static class StubForumContext implements ForumContext
    {
        public String encodeURL(String url)
        {
            return url;
        }

        public String encodeURL(String url, String extension)
        {
            return url + extension;
        }

        public boolean isEncodingDisabled()
        {
            return false;
        }

        public RequestContext getRequest()
        {
            return null;
        }

        public ResponseContext getResponse()
        {
            return null;
        }

        public boolean isBot()
        {
            return false;
        }
    }

    public void testFinishClearsContext()
    {
        JForumExecutionContext context = JForumExecutionContext.get();
        context.setForumContext(new StubForumContext());
        JForumExecutionContext.setRedirect("somewhere");
        JForumExecutionContext.enableCustomContent(true);

        assertEquals("somewhere", JForumExecutionContext.getRedirectTo());
        assertTrue(JForumExecutionContext.isCustomContent());
        assertNotNull(JForumExecutionContext.getForumContext());

        JForumExecutionContext.finish();

        assertNull(JForumExecutionContext.getRedirectTo());
        assertFalse(JForumExecutionContext.isCustomContent());
        assertNull(JForumExecutionContext.getForumContext());
    }

    public void testTypedForumContextAccessor()
    {
        ForumContext forumContext = new StubForumContext();
        JForumExecutionContext.get().setForumContext(forumContext);

        ForumContext retrieved = JForumExecutionContext.getForumContext();
        assertSame(forumContext, retrieved);
    }
}

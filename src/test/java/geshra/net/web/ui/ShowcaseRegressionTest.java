package geshra.net.web.ui;

import geshra.net.web.RouteRegistry;
import geshra.net.web.SessionContext;
import geshra.net.web.WebFileType;
import geshra.net.web.ui.components.Image;
import geshra.net.web.ui.components.Form;
import geshra.net.web.ui.components.NativeTextField;
import geshra.net.web.ui.components.InputField;
import geshra.net.web.ui.event.ValueChangeEvent;
import geshra.net.web.ui.components.tree.Tree;
import geshra.net.web.ui.components.tree.TreeNode;
import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regressions for defects exposed by the real component showcase: tree label initialization and
 * subtree-preserving rename, and image alternative text. Tests decode actual session DOM packets;
 * independent browser tests verify their visible/accessibility effects.
 */
class ShowcaseRegressionTest {
    /**
     * Local media resources advertise their actual formats instead of a generic download type.
     */
    @Test
    void resolvesNativeAudioAndVideoMimeTypes() {
        assertThat(WebFileType.fromFilename("/showcase-assets/sample.WAV").getContentType())
                .isEqualTo("audio/wav");
        assertThat(WebFileType.fromFilename("/showcase-assets/sample.webm").getContentType())
                .isEqualTo("video/webm");
    }

    /**
     * Temporarily detached controls are omitted without losing their retained binding on reattach.
     */
    @Test
    void restoresFormBindingAfterRetainedFieldReattachment() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "showcase-form-reattach", channel);
        SessionContext.set(context);
        try {
            Form form = new Form();
            NativeTextField field = new NativeTextField("text", "Retained");
            form.addFormItem("name", field);
            context.getUI()
                    .add(form);
            field.detach();
            assertThat(form.getValues())
                    .doesNotContainKey("name");
            form.add(field);
            assertThat(form.getValues())
                    .containsEntry("name", "Retained");
            field.setValueAndNotify("Reattached update");
            assertThat(form.getValues())
                    .containsEntry("name", "Reattached update");
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Removed controls disappear from values, clear releases bindings and duplicate names use only
     * the newest component even when an older control later publishes an event.
     */
    @Test
    void releasesRemovedFormBindingsAndReplacesDuplicateNames() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "showcase-form-lifecycle", channel);
        SessionContext.set(context);
        try {
            Form form = new Form();
            NativeTextField removed = new NativeTextField("text", "Removed");
            form.addFormItem("removed", removed);
            context.getUI()
                    .add(form);
            form.remove(removed);
            assertThat(form.getValues())
                    .doesNotContainKey("removed");
            NativeTextField old = new NativeTextField("text", "Old");
            form.addFormItem("name", old);
            InputField generic = new InputField();
            form.addFormItem("name", generic);
            generic.publish(new ValueChangeEvent(null, null, "Generic callback"));
            old.setValueAndNotify("Obsolete");
            assertThat(form.getValues())
                    .containsEntry("name", "Generic callback");
            form.clear();
            assertThat(form.getValues())
                    .isEmpty();
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Submission includes untouched defaults and subsequent silent browser/programmatic changes.
     */
    @Test
    void collectsCurrentTypedFormValuesWithoutRequiringInputEvents() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "showcase-form", channel);
        SessionContext.set(context);
        try {
            Form form = new Form();
            NativeTextField name = new NativeTextField("text", "Ada");
            form.addFormItem("name", name);
            form.addCheckbox("updates", "Receive updates");
            context.getUI()
                    .add(form);
            assertThat(form.getValues())
                    .containsEntry("name", "Ada")
                    .containsEntry("updates", false);
            name.setValue("Grace");
            assertThat(form.getValues())
                    .containsEntry("name", "Grace");
            name.synchronizeValue("Browser normalized");
            assertThat(form.getValues())
                    .containsEntry("name", "Browser normalized");
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Initial labels survive child creation and rename targets only the retained label node.
     */
    @Test
    void initializesAndRenamesTreeLabelsWithoutReplacingBranches() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "showcase-tree", channel);
        SessionContext.set(context);
        try {
            UI ui = context.getUI();
            ui.beginUpdate();
            TreeNode root = new TreeNode("Project");
            TreeNode branch = new TreeNode("Sources");
            TreeNode leaf = new TreeNode("Main.java");
            branch.addNode(leaf);
            root.addNode(branch);
            ui.add(new Tree(root));
            ui.endUpdate();
            Map<Integer, String> initial = drainText(channel);
            assertThat(initial.values())
                    .contains("Project", "Sources", "Main.java");
            Integer label = initial.entrySet()
                    .stream()
                    .filter(entry -> entry.getValue().equals("Sources"))
                    .findFirst()
                    .orElseThrow()
                    .getKey();
            branch.setValue("Renamed sources");
            Map<Integer, String> renamed = drainText(channel);
            assertThat(renamed)
                    .containsEntry(label, "Renamed sources")
                    .doesNotContainKey(branch.getComponentID());
            assertThat(leaf.isAttached())
                    .isTrue();
            assertThat(branch.getValue())
                    .isEqualTo("Renamed sources");
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Image description is emitted as the standard alternative-text attribute.
     */
    @Test
    void exposesNativeImageAlternativeText() {
        EmbeddedChannel channel = new EmbeddedChannel();
        SessionContext context = new SessionContext(new RouteRegistry(List.of()), "showcase-image", channel);
        SessionContext.set(context);
        try {
            context.getUI()
                    .add(new Image("/sample.svg", "Accessible illustration"));
            StringBuilder wire = new StringBuilder();
            BinaryWebSocketFrame frame;
            while ((frame = channel.readOutbound()) != null) {
                try {
                    wire.append(frame.content().toString(StandardCharsets.UTF_8));
                } finally {
                    frame.release();
                }
            }
            assertThat(wire.toString())
                    .contains("alt", "Accessible illustration")
                    .doesNotContain("description");
        } finally {
            SessionContext.clear();
            channel.finishAndReleaseAll();
        }
    }

    /**
     * Reconstructs final text mutations from emitted legacy DOM batches and releases every frame.
     * @param channel owned embedded transport
     * @return final text by target component ID
     */
    private static Map<Integer, String> drainText(EmbeddedChannel channel) {
        /*
         * Later mutations replace earlier values, reproducing the browser's text-update ordering.
         */
        Map<Integer, String> text = new HashMap<>();
        BinaryWebSocketFrame frame;
        while ((frame = channel.readOutbound()) != null) {
            try {
                ByteBuf bytes = frame.content();
                assertThat((int) bytes.readUnsignedByte())
                        .isEqualTo(1);
                int count = bytes.readUnsignedShort();
                for (int index = 0; index < count; index++) {
                    int type = bytes.readUnsignedByte();
                    int id = bytes.readInt();
                    int parameters = bytes.readUnsignedByte();
                    for (int parameter = 0; parameter < parameters; parameter++) {
                        int key = bytes.readUnsignedByte();
                        int length = bytes.readUnsignedShort();
                        String value = bytes.readCharSequence(length, StandardCharsets.UTF_8)
                                .toString();
                        if (type == DOMUpdateType.SET_TEXT.getCode() && key == DOMUpdateParam.TEXT.getCode()) text.put(id, value);
                    }
                }
            } finally {
                frame.release();
            }
        }
        return text;
    }
}

package net.maddkraft.maddprestige.platform.paper.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.maddkraft.maddprestige.core.admin.presentation.MessageReference;
import net.maddkraft.maddprestige.platform.paper.i18n.PaperMessageService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PhaseSevenCommandExecutorPresentationTest {
    @TempDir
    Path directory;

    @Test
    @DisplayName("[A68][OR8D-01] Phase 7 Paper command renders semantic supplier references through selected catalog")
    void selectedCatalogControlsActualPhaseSevenCommandOutput() throws Exception {
        PaperMessageService messages = PaperMessageService.open(directory,
                PaperMessageService.class.getClassLoader(), ignored -> { });
        Files.writeString(directory.resolve("locale.yml"), "locale: zz_ZZ\n", StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("locales/zz_ZZ.yml"),
                "command.status.configuration: \"FAZA <revision>\"\n", StandardCharsets.UTF_8);
        messages.reload();
        PhaseSevenCommandExecutor executor = new PhaseSevenCommandExecutor(
                () -> List.of(MessageReference.of("command.status.configuration", "revision", "r7")),
                List::of, List::of, messages);
        CommandSender sender = mock(CommandSender.class);
        AtomicReference<Component> sent = new AtomicReference<>();
        doAnswer(invocation -> {
            sent.set(invocation.getArgument(0));
            return null;
        }).when(sender).sendMessage(any(Component.class));

        executor.onCommand(sender, mock(Command.class), "maddprestige", new String[] {"status"});

        assertEquals("FAZA r7", PlainTextComponentSerializer.plainText().serialize(sent.get()));
    }
}

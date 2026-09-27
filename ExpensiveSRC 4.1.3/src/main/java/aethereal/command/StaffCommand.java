package aethereal.command;
import aethereal.util.ChatUtil;
import aethereal.model.CommandContext;
import aethereal.Expensive;
import aethereal.util.FriendManager;
import aethereal.util.StaffDetector;
import aethereal.model.TranslatedException;
import aethereal.model.Translation;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public class StaffCommand implements ClientCommand {
    @Override
    public String getName() {
        return "staff";
    }

    @Override
    public Translation getDescription() {
        return Translation.clearText("Управление собственным staff-листом");
    }

    @Override
    public String getUsage() {
        return ".staff <add | remove | list | clear> [name]";
    }

    @Override
    public List<String> getAliases() {
        return List.of("stafflist");
    }

    @Override
    public void execute(CommandContext class392Var) throws TranslatedException {
        String[] strArrArgs = class392Var.args();
        if (strArrArgs.length == 0) {
            throw new TranslatedException(Translation.clearText("Укажи подкоманду. Использование: " + getUsage()));
        }
        String lowerCase = strArrArgs[0].toLowerCase();
        String[] strArr = (String[]) Arrays.copyOfRange(strArrArgs, 1, strArrArgs.length);
        switch (lowerCase) {
            case "add":
                addStaff(strArr);
                return;
            case "remove":
                removeStaff(strArr);
                return;
            case "list":
                listStaff();
                return;
            case "clear":
                clearStaff();
                return;
            default:
                throw new TranslatedException(Translation.clearText("Неизвестная подкоманда: " + lowerCase));
        }
    }

    public void addStaff(String[] strArr) throws TranslatedException {
        if (strArr.length < 1) {
            throw new TranslatedException(Translation.clearText("Укажи ник для добавления"));
        }
        String str = strArr[0];
        if (!StaffDetector.addCustomStaffName(str)) {
            throw new TranslatedException(Translation.clearText("Этот ник уже есть в staff-листе: " + str));
        }
        ChatUtil.addChatMessage((Text) Text.literal("Добавлен в staff-лист: " + str).formatted(Formatting.GRAY));
        saveStaff();
    }

    public void removeStaff(String[] strArr) throws TranslatedException {
        if (strArr.length < 1) {
            throw new TranslatedException(Translation.clearText("Укажи ник для удаления"));
        }
        String str = strArr[0];
        if (!StaffDetector.removeCustomStaffName(str)) {
            throw new TranslatedException(Translation.clearText("Ник не найден в staff-листе: " + str));
        }
        ChatUtil.addChatMessage((Text) Text.literal("Удален из staff-листа: " + str).formatted(Formatting.GRAY));
        saveStaff();
    }

    public void listStaff() {
        Set<String> customStaffNames = StaffDetector.getCustomStaffNames();
        if (customStaffNames.isEmpty()) {
            ChatUtil.addChatMessage((Text) Text.literal("Пользовательский staff-лист пуст").formatted(Formatting.GRAY));
        } else {
            ChatUtil.addChatMessage((Text) Text.literal("Пользовательский staff-лист: " + ((String) customStaffNames.stream().sorted().collect(Collectors.joining(", ")))).formatted(Formatting.GRAY));
        }
    }

    public void clearStaff() {
        StaffDetector.clearCustomStaffNames();
        ChatUtil.addChatMessage((Text) Text.literal("Пользовательский staff-лист очищен").formatted(Formatting.GRAY));
        saveStaff();
    }

    public void saveStaff() {
        try {
            Expensive.INSTANCE.configManager().saveStaff();
        } catch (Exception e) {
            Expensive.LOGGER.error("Failed to save staff list", e);
        }
    }

    @Override
    public List<String> getSuggestions(String[] strArr, int i) {
        if (i == 0) {
            return Stream.of(new String[]{"add", "remove", "list", "clear"}).filter(str -> {
                return str.startsWith(strArr[0].toLowerCase());
            }).toList();
        }
        if (i == 1) {
            String lowerCase = strArr[0].toLowerCase();
            String str2 = i < strArr.length ? strArr[i] : "";
            if (lowerCase.equals("remove")) {
                String lowerCase2 = str2.toLowerCase();
                return StaffDetector.getCustomStaffNames().stream().filter(str3 -> {
                    return str3.toLowerCase().startsWith(lowerCase2);
                }).sorted().toList();
            }
            if (lowerCase.equals("add")) {
                String lowerCase3 = str2.toLowerCase();
                MinecraftClient minecraftClient = MinecraftClient.getInstance();
                if (minecraftClient.player == null || minecraftClient.getNetworkHandler() == null) {
                    return List.of();
                }
                Set<String> friends = FriendManager.getFriends();
                return minecraftClient.getNetworkHandler().getPlayerList().stream().map(playerListEntry -> {
                    return playerListEntry.getProfile().getName();
                }).filter(str4 -> {
                    return str4.toLowerCase().startsWith(lowerCase3);
                }).filter(str5 -> {
                    return !friends.contains(str5);
                }).sorted().toList();
            }
        }
        return List.of();
    }
}

/**
 * Plugin for Faggot of the Day game in MUCs.
 * Copyright (C) 2017 Kona-chan
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.rkfg.xmpp.bot.plugins;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smackx.muc.MultiUserChat;
import org.jivesoftware.smackx.muc.Occupant;
import org.jxmpp.util.XmppStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ru.ppsrk.gwt.client.ClientAuthException;
import ru.ppsrk.gwt.client.LogicException;

/**
 * Lets users of MUCs play Faggot of the Day.
 *
 * @author Kona-chan
 * @version 0.3.0
 */
public class FaggotOfTheDayPlugin2 extends CommandPlugin {

    private static final List<String> ALL_COMMANDS = Arrays.asList("pidor", "пидор");
    private static final String INFO_FAGGOT_PENDING = "Пидор дня вычисляется.";
    private static final String INFO_FAGGOT_IS = "сегодня Пидор дня — ";
    private static final String INFO_FAGGOT_IS_YOU = INFO_FAGGOT_IS + "ты!";

    private boolean isListening = false;
    private Timer timer;

    private final Set<Occupant> occupants = new HashSet<>();
    private Occupant faggot;

    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final Random random = new Random();

    @Override
    public String processCommand(Message message, Matcher matcher)
            throws LogicException, ClientAuthException {
        startListening();
        startTimer();

        if (faggot == null) {
            return INFO_FAGGOT_PENDING;
        }

        final Occupant sender = getMUCManager().getMUCOccupant(message.getFrom());
        final String senderJid = XmppStringUtils.parseBareJid(sender.getJid());
        final String faggotJid = XmppStringUtils.parseBareJid(faggot.getJid());
        if (senderJid.equals(faggotJid)) {
            return INFO_FAGGOT_IS_YOU;
        }

        final String roomJid = XmppStringUtils.parseBareJid(message.getFrom());
        final Occupant occupant = Optional
                .of(getMUCManager().listMUCOccupantsByJID(roomJid).get(faggotJid))
                .orElse(faggot);

        return INFO_FAGGOT_IS + occupant.getNick() + ".";
    }

    @Override
    public List<String> getCommand() {
        return ALL_COMMANDS;
    }

    @Override
    public String getManual() {
        final String commands = StringUtils.join(ALL_COMMANDS, "|");
        final String sampleCommand = PREFIX + ALL_COMMANDS.get(0);

        return
                "узнать, кто сегодня Пидор дня.\n" +
                "Формат: <" + commands + ">\n" +
                "Пример: " + sampleCommand;
    }

    private void startListening() {
        if (isListening) {
            return;
        }

        isListening = true;

        getMUCManager().listMUCs().forEach(muc -> {
            muc.addMessageListener(message -> {
               occupants.add(muc.getOccupant(message.getFrom()));
            });

            occupants.addAll(getAllOccupants(muc));
        });
    }

    private void startTimer() {
        if (timer != null) {
            return;
        }

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                logger.info("🌚 A new day starts in the Empire 🌝");

                occupants.removeIf(occupant -> {
                    final String bareJid = XmppStringUtils.parseBareJid(occupant.getJid());
                    return bareJid.equals(getBotJid());
                });

                if (occupants.isEmpty()) {
                    logger.info("No contenders for Faggot of the Day today.");
                    if (faggot != null) {
                        logger.info("{} remains Faggot of the Day!", faggot.getNick());
                    }
                    return;
                }

                final String contenders = occupants.stream().map(Occupant::getNick)
                            .collect(Collectors.joining(", "));
                logger.info("Contenders for today’s Faggot of the Day title: {}", contenders);

                final int i = random.nextInt(occupants.size());
                faggot = occupants.stream().skip(i).findFirst().get();
                logger.info("{} becomes Faggot of the Day!", faggot.getNick());

                occupants.clear();
            }

        }, getFirstTime(), getPeriod());
    }

    private Date getFirstTime() {
        final LocalTime midnight = LocalTime.MIDNIGHT;
        final LocalDate today = LocalDate.now();
        final LocalDateTime todayMidnight = LocalDateTime.of(today, midnight);
        return Date.from(todayMidnight.atZone(ZoneId.systemDefault()).toInstant());
    }

    private long getPeriod() {
        return TimeUnit.MILLISECONDS.convert(1, TimeUnit.DAYS);
    }

    private Set<Occupant> getAllOccupants(MultiUserChat muc) {
        return muc.getOccupants().stream().map(muc::getOccupant).filter(occupant -> {

            final String jid = occupant.getJid();
            if (jid == null) {
                return false;
            }

            final String bareJid = XmppStringUtils.parseBareJid(jid);
            return !bareJid.equals(getBotJid());

        }).collect(Collectors.toSet());
    }

    private String getBotJid() {
        final String login = getSettingsManager().getStringSetting("login");
        final String server = getSettingsManager().getStringSetting("server");
        return XmppStringUtils.completeJidFrom(login, server);
    }

}

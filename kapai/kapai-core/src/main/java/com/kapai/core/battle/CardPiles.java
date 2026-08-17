package com.kapai.core.battle;

import com.kapai.core.card.AbstractCard;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * 牌堆管理：抽牌堆、手牌、弃牌堆。
 *
 * 设计思路：单战斗生命周期对象，由 {@link BattleManager} 持有。
 * 抽牌堆为空时自动洗弃牌堆为新抽牌堆（标准杀戮尖塔规则）。
 */
@Data
@Slf4j
public class CardPiles {

    private final Deque<AbstractCard> drawPile = new ArrayDeque<>();
    private final List<AbstractCard> hand = new ArrayList<>();
    private final List<AbstractCard> discardPile = new ArrayList<>();

    /** 洗牌：把弃牌堆洗入抽牌堆。 */
    public void reshuffle() {
        Collections.shuffle(discardPile);
        for (AbstractCard c : discardPile) {
            drawPile.push(c);
        }
        discardPile.clear();
        log.debug("洗牌：弃牌堆 {} 张 → 抽牌堆", drawPile.size());
    }

    /** 从抽牌堆顶抽 n 张到手牌；不足时先洗弃牌堆再抽。 */
    public void draw(int n) {
        for (int i = 0; i < n; i++) {
            if (drawPile.isEmpty()) {
                if (discardPile.isEmpty()) {
                    log.debug("无牌可抽");
                    return;
                }
                reshuffle();
            }
            hand.add(drawPile.pop());
        }
    }

    /** 从手牌打出一张牌（移出手牌）。 */
    public void removeFromHand(AbstractCard card) {
        hand.remove(card);
    }

    /** 将一张牌置入弃牌堆。 */
    public void discard(AbstractCard card) {
        discardPile.add(card);
    }

    /** 回合结束：弃置全部手牌。 */
    public void discardHand() {
        discardPile.addAll(hand);
        hand.clear();
    }

    public void initDrawPile(List<AbstractCard> deck) {
        List<AbstractCard> shuffled = new ArrayList<>(deck);
        Collections.shuffle(shuffled);
        shuffled.forEach(drawPile::push);
    }
}

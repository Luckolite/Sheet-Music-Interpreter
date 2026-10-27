# Literal connected prefixes in beam stem scanning

A raw stem column may include a single disconnected speck beyond a real connected stem. The scanner tolerates one empty sample while collecting a column, but the later component check formerly rejected the entire column when that sample disconnected its remote cap. Adding an unrelated pixel could therefore erase an otherwise valid stem.

The contrast evidence path now retains the literal head-connected prefix when every retained column independently satisfies the existing minimum length and density requirements. Column endpoints and saved pixel rows are clipped together. No gap pixel is supplied, and disconnected farther ink is excluded. Source ownership, rival decisions, and downstream contact with a printed beam remain required.

Strict strong-ink scanning keeps its complete-cap rule. A pale but real cell inside a stem can connect a full contrast-supported shaft without forming a full strict shaft. Creating a shortened strict core in that case would incorrectly prefer its earlier endpoint over the actual beam contact.

The original synthetic regression draws simple staff rules, note bodies, stems, and a single beam. Expected behavior is one connected beam group with bridge mask1 for a continuous printed stem, including a pale internal stem cell. A real gap cannot borrow a farther beam. Additional cases cover detached specks, reflected directions, short fragments, uniform gray paper, opposing shaft rivals, unchanged raster/source identity, and exact/refused work budgets. The fixtures and tests are original contributions under Apache-2.0; they contain no external score content. No model weights or scalar acceptance thresholds change.

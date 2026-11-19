# AafatLink visual system

## Direction

AafatLink uses an **Operate** interface: the design stays quiet so requesters, volunteers, coordinators and administrators can complete time-sensitive work without learning unusual controls. The visual language is restrained, trustworthy and operational. It avoids decorative motion, dense ornament and color used without meaning.

## Application frame

- A deep navy sidebar (`#0B2136`) carries the AafatLink identity, role-aware destinations, sign-out action and session notice.
- A white top bar names the current destination and keeps the signed-in identity visible.
- The working surface uses cool gray (`#F3F6F9`) with white content panels.
- Authentication uses the same navy and teal language in a split layout. Demonstration access is labelled separately from connected sign-in.
- The shell opens at 1280 by 700 pixels, has a 1020 by 680 minimum, and places long forms inside vertical scroll panes. Request tracking uses a split list-and-detail layout.

## Color roles

| Role | Value | Use |
|---|---:|---|
| Navigation | `#0B2136` | Sidebar and strongest brand surface |
| Navy action | `#102A43` | Secondary actions and brand structure |
| Primary teal | `#0F766E` | Primary actions, focus, selected state and status emphasis |
| Teal soft | `#E6F4F2` | Selected and informational backgrounds |
| Workspace | `#F3F6F9` | Main application background |
| Border | `#DCE4EA` | Panel and structural separation |
| Primary text | `#172B3A` | Headings and high-priority content |
| Muted text | `#607487` | Supporting copy and metadata |
| Error | `#B42318` | Validation and failed-operation feedback |

Warnings use an amber-tinted surface with dark text. Status and lifecycle treatments always include readable text; color is supporting information, not the only signal.

## Typography

The application uses the native-friendly `Segoe UI` stack with a 14-pixel root size. One sans-serif family carries headings, labels, controls and data. The scale is compact: 25 to 31 pixels for major titles, 17 to 20 pixels for panel and page titles, 12 to 15 pixels for body content, and 9 to 11 pixels for metadata. Weight and spacing provide hierarchy without display typography.

## Spacing and shape

- Layout spacing follows a practical 4-to-8-pixel rhythm, with page padding around 28 to 42 pixels and component gaps from 8 to 24 pixels.
- Inputs and buttons use 8-to-9-pixel radii.
- Content panels use 12-pixel radii; the authentication card uses 18 pixels as the single higher-emphasis exception.
- Status chips are compact pills. Larger panels and forms remain softly rectangular.
- Panels use either a light border or a restrained shadow according to hierarchy. Nested decorative cards are avoided.

## Components and states

- Navigation items combine one Ikonli icon family with a text label and a visible selected state.
- Primary, secondary, outline and text buttons have distinct roles. Buttons provide hover, focus, pressed and disabled feedback.
- Forms use persistent labels, helper text, required-field markers and contextual success or error messages.
- Request lifecycle steps show current, completed, upcoming and alternative states with both text and surface treatment.
- Tables use calm headers, 48-pixel rows, hover feedback and a clearly selected row.
- Request tracking provides loading, empty, error-with-retry and detail-placeholder states.
- Demonstration sessions are explicitly read-only and never presented as authenticated operational data.

## Motion

Motion is minimal. Buttons move down by one pixel when pressed to acknowledge input. Screen navigation and common task actions do not use decorative entrance sequences or delays.

## Accessibility and resizing

Controls retain visible labels and keyboard focus styling. Navigation uses icons plus text, and status uses words plus color. Long copy wraps, form pages scroll, and the tracking screen preserves separate list and detail regions. The current desktop layout targets a minimum width of 1020 pixels; narrower responsive navigation is not implemented yet and must not be claimed.

## Implemented surfaces

The system currently covers login, registration, password recovery, the reusable role-aware shell, dashboard, UC01 request submission/resubmission presentation, and the UC02 requester list/detail presentation. Other role destinations inherit this system when their working screens are implemented.

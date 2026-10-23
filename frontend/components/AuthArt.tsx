import Scene3D from "./Scene3D";

/**
 * The full-screen backdrop behind sign-in and register: soft color pools with
 * the 3D scene over them, and a caption under the scene's focal point.
 */
export default function AuthArt({
  caption,
  focus,
  active,
}: {
  caption: string;
  focus: number;
  active: boolean;
}) {
  return (
    <div aria-hidden className="relative h-full w-full overflow-hidden bg-[#0a0516]">
      <div className="art-wash" />
      <Scene3D focus={focus} active={active} />
      <p
        key={caption}
        className="art-caption rise serif text-2xl font-extrabold italic leading-tight"
      >
        {caption}
      </p>
    </div>
  );
}

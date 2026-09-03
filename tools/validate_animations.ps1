[CmdletBinding()]
param(
    [string]$AnimationPath = "app\src\main\assets\exercise_animations.json",
    [string]$MovementLibraryPath = "curriculum\movement_library.json",
    [string]$ForensicExportPath = "street_gymnastic_programs_levels.json"
)

# Validates the schema-2 offline exercise animations: an articulated side-view figure with
# fixed bone lengths, poses stored as absolute joint angles plus one pinned joint, and
# templates made of keyframes. Besides structural checks it runs the same forward kinematics
# as the app so every keyframe is verified to stay inside the drawing canvas.

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$errors = New-Object System.Collections.Generic.List[string]

function Add-ValidationError {
    param([string]$Message)
    $script:errors.Add($Message)
}

function Resolve-ProjectPath {
    param([string]$Path)
    if ([System.IO.Path]::IsPathRooted($Path)) { return $Path }
    return Join-Path $projectRoot $Path
}

function Read-JsonFile {
    param([string]$Path, [string]$Label)
    $resolved = Resolve-ProjectPath $Path
    if (-not (Test-Path -LiteralPath $resolved -PathType Leaf)) {
        Add-ValidationError "$Label is missing: $resolved"
        return $null
    }
    try {
        return Get-Content -Raw -LiteralPath $resolved -Encoding UTF8 | ConvertFrom-Json
    }
    catch {
        Add-ValidationError "$Label is not valid JSON: $($_.Exception.Message)"
        return $null
    }
}

function Has-Property {
    param([AllowNull()]$Object, [string]$Name)
    if ($null -eq $Object) { return $false }
    return $null -ne $Object.PSObject.Properties[$Name]
}

function Get-Properties {
    param([AllowNull()]$Object)
    if ($null -eq $Object) { return @() }
    return @($Object.PSObject.Properties)
}

function Test-JsonObject { param([AllowNull()]$Value) return $null -ne $Value -and $Value -is [pscustomobject] }
function Test-JsonArray { param([AllowNull()]$Value) return $null -ne $Value -and $Value -is [System.Array] }

function Test-JsonNumber {
    param([AllowNull()]$Value)
    if ($null -eq $Value -or $Value -is [bool]) { return $false }
    return $Value -is [byte] -or $Value -is [sbyte] -or $Value -is [int16] -or $Value -is [uint16] -or
        $Value -is [int32] -or $Value -is [uint32] -or $Value -is [int64] -or $Value -is [uint64] -or
        $Value -is [single] -or $Value -is [double] -or $Value -is [decimal]
}

function Test-FiniteNumber {
    param([AllowNull()]$Value)
    if (-not (Test-JsonNumber $Value)) { return $false }
    $number = [double]$Value
    return -not [double]::IsNaN($number) -and -not [double]::IsInfinity($number)
}

function Normalize-Key {
    param([AllowNull()][string]$Value)
    if ($null -eq $Value) { return "" }
    return (($Value.ToLowerInvariant() -replace '[^a-z0-9]+', '_').Trim('_'))
}

$jointNames = @(
    "hip", "neck", "head", "shoulder", "elbow_near", "hand_near", "elbow_far", "hand_far",
    "knee_near", "foot_near", "toe_near", "knee_far", "foot_far", "toe_far", "hands", "feet"
)

# ---------------------------------------------------------------------------------------------
# Pose model (mirrors ExerciseAnimationView.kt)
# ---------------------------------------------------------------------------------------------
function New-Pose {
    return [pscustomobject]@{
        facing = 1.0; torso = 0.0; head = $null; spine = 0.0; shrug = 0.0; protract = 0.0
        arm_near = @(180.0, 180.0); arm_far = @(180.0, 180.0)
        leg_near = @(180.0, 180.0); leg_far = @(180.0, 180.0)
        foot_near = $null; foot_far = $null
        pin_joint = "hip"; pin_x = 100.0; pin_y = 73.0
    }
}

function Copy-Pose {
    param($Pose)
    $copy = New-Pose
    foreach ($property in $Pose.PSObject.Properties) {
        if ($property.Value -is [System.Array]) { $copy.($property.Name) = @($property.Value) }
        else { $copy.($property.Name) = $property.Value }
    }
    return $copy
}

function Test-AnglePair {
    param([AllowNull()]$Value, [string]$Location)
    if (-not (Test-JsonArray $Value) -or @($Value).Count -ne 2) {
        Add-ValidationError "$Location must contain exactly two angles in degrees"
        return $false
    }
    foreach ($angle in @($Value)) {
        if (-not (Test-FiniteNumber $angle)) { Add-ValidationError "$Location must contain finite numbers"; return $false }
    }
    return $true
}

function Test-OptionalAngle {
    param($Object, [string]$Name, [string]$Location)
    if (-not (Has-Property $Object $Name)) { return }
    $value = $Object.$Name
    if ($null -eq $value) { return }
    if (-not (Test-FiniteNumber $value)) { Add-ValidationError "$Location.$Name must be a number or null" }
}

function Apply-PoseJson {
    <# Applies raw JSON fields on top of a pose object while validating them. #>
    param($Pose, $Json, [string]$Location)
    if (Has-Property $Json "facing") {
        if (-not (Test-FiniteNumber $Json.facing) -or ([double]$Json.facing -ne 1 -and [double]$Json.facing -ne -1)) {
            Add-ValidationError "$Location.facing must be 1 or -1"
        }
        else { $Pose.facing = [double]$Json.facing }
    }
    foreach ($field in @("torso", "spine", "shrug", "protract")) {
        if (Has-Property $Json $field) {
            if (-not (Test-FiniteNumber $Json.$field)) { Add-ValidationError "$Location.$field must be a finite number" }
            else { $Pose.$field = [double]$Json.$field }
        }
    }
    if ([math]::Abs($Pose.spine) -gt 12) { Add-ValidationError "$Location.spine must be within -12 through 12" }
    if ([math]::Abs($Pose.shrug) -gt 6) { Add-ValidationError "$Location.shrug must be within -6 through 6" }
    if ([math]::Abs($Pose.protract) -gt 6) { Add-ValidationError "$Location.protract must be within -6 through 6" }
    Test-OptionalAngle $Json "head" $Location
    if (Has-Property $Json "head") { $Pose.head = if ($null -eq $Json.head) { $null } else { [double]$Json.head } }
    if (Has-Property $Json "arms") {
        if (Test-AnglePair $Json.arms "$Location.arms") { $Pose.arm_near = @([double]$Json.arms[0], [double]$Json.arms[1]); $Pose.arm_far = @($Pose.arm_near) }
    }
    if (Has-Property $Json "legs") {
        if (Test-AnglePair $Json.legs "$Location.legs") { $Pose.leg_near = @([double]$Json.legs[0], [double]$Json.legs[1]); $Pose.leg_far = @($Pose.leg_near) }
    }
    foreach ($field in @("arm_near", "arm_far", "leg_near", "leg_far")) {
        if (Has-Property $Json $field) {
            if (Test-AnglePair $Json.$field "$Location.$field") { $Pose.$field = @([double]$Json.$field[0], [double]$Json.$field[1]) }
        }
    }
    Test-OptionalAngle $Json "feet" $Location
    if (Has-Property $Json "feet") {
        $value = if ($null -eq $Json.feet) { $null } else { [double]$Json.feet }
        $Pose.foot_near = $value; $Pose.foot_far = $value
    }
    foreach ($field in @("foot_near", "foot_far")) {
        Test-OptionalAngle $Json $field $Location
        if (Has-Property $Json $field) { $Pose.$field = if ($null -eq $Json.$field) { $null } else { [double]$Json.$field } }
    }
    if ((Has-Property $Json "pin") -and (Has-Property $Json "hip")) {
        Add-ValidationError "$Location must not define both pin and hip"
    }
    if (Has-Property $Json "pin") {
        $pin = $Json.pin
        if (-not (Test-JsonObject $pin)) { Add-ValidationError "$Location.pin must be an object" }
        else {
            $joint = if (Has-Property $pin "joint") { [string]$pin.joint } else { "hip" }
            if ($joint -notin $jointNames) { Add-ValidationError "$Location.pin.joint '$joint' is not a known joint" }
            else { $Pose.pin_joint = $joint }
            if (-not (Has-Property $pin "at") -or -not (Test-JsonArray $pin.at) -or @($pin.at).Count -ne 2 -or
                -not (Test-FiniteNumber $pin.at[0]) -or -not (Test-FiniteNumber $pin.at[1])) {
                Add-ValidationError "$Location.pin.at must be an [x, y] pair"
            }
            else { $Pose.pin_x = [double]$pin.at[0]; $Pose.pin_y = [double]$pin.at[1] }
        }
    }
    if (Has-Property $Json "hip") {
        if (-not (Test-JsonArray $Json.hip) -or @($Json.hip).Count -ne 2 -or
            -not (Test-FiniteNumber $Json.hip[0]) -or -not (Test-FiniteNumber $Json.hip[1])) {
            Add-ValidationError "$Location.hip must be an [x, y] pair"
        }
        else { $Pose.pin_joint = "hip"; $Pose.pin_x = [double]$Json.hip[0]; $Pose.pin_y = [double]$Json.hip[1] }
    }
}

function Get-Direction {
    param([double]$Angle)
    $radians = $Angle * [math]::PI / 180.0
    $x = [double][math]::Sin($radians)
    $y = 0.0 - [double][math]::Cos($radians)
    return ,@($x, $y)
}

function Solve-Pose {
    <# Forward kinematics in canvas units; returns a hashtable joint -> @(x, y). #>
    param($Pose, $Segments)
    $joints = @{}
    $joints["hip"] = @(0.0, 0.0)
    $torsoDir = Get-Direction $Pose.torso
    $joints["neck"] = @(($torsoDir[0] * $Segments.torso), ($torsoDir[1] * $Segments.torso))
    $headAngle = if ($null -eq $Pose.head) { $Pose.torso } else { $Pose.head }
    $headDir = Get-Direction $headAngle
    $headLength = $Segments.neck + $Segments.head_radius
    $joints["head"] = @(($joints["neck"][0] + $headDir[0] * $headLength), ($joints["neck"][1] + $headDir[1] * $headLength))
    $front = @(((0.0 - $torsoDir[1]) * $Pose.facing), ($torsoDir[0] * $Pose.facing))
    $joints["shoulder"] = @(
        ($joints["neck"][0] + $torsoDir[0] * $Pose.shrug + $front[0] * $Pose.protract),
        ($joints["neck"][1] + $torsoDir[1] * $Pose.shrug + $front[1] * $Pose.protract)
    )
    function Step-Joint {
        param([string]$Name, [string]$From, [double]$Angle, [double]$Length)
        $dir = Get-Direction $Angle
        $joints[$Name] = @(($joints[$From][0] + $dir[0] * $Length), ($joints[$From][1] + $dir[1] * $Length))
    }
    Step-Joint "elbow_near" "shoulder" $Pose.arm_near[0] $Segments.upper_arm
    Step-Joint "hand_near" "elbow_near" $Pose.arm_near[1] $Segments.forearm
    Step-Joint "elbow_far" "shoulder" $Pose.arm_far[0] $Segments.upper_arm
    Step-Joint "hand_far" "elbow_far" $Pose.arm_far[1] $Segments.forearm
    Step-Joint "knee_near" "hip" $Pose.leg_near[0] $Segments.thigh
    Step-Joint "foot_near" "knee_near" $Pose.leg_near[1] $Segments.shin
    $footNear = if ($null -eq $Pose.foot_near) { $Pose.leg_near[1] - 90.0 * $Pose.facing } else { $Pose.foot_near }
    Step-Joint "toe_near" "foot_near" $footNear $Segments.foot
    Step-Joint "knee_far" "hip" $Pose.leg_far[0] $Segments.thigh
    Step-Joint "foot_far" "knee_far" $Pose.leg_far[1] $Segments.shin
    $footFar = if ($null -eq $Pose.foot_far) { $Pose.leg_far[1] - 90.0 * $Pose.facing } else { $Pose.foot_far }
    Step-Joint "toe_far" "foot_far" $footFar $Segments.foot
    $joints["hands"] = @((($joints["hand_near"][0] + $joints["hand_far"][0]) / 2), (($joints["hand_near"][1] + $joints["hand_far"][1]) / 2))
    $joints["feet"] = @((($joints["foot_near"][0] + $joints["foot_far"][0]) / 2), (($joints["foot_near"][1] + $joints["foot_far"][1]) / 2))
    $anchor = $joints[$Pose.pin_joint]
    $offsetX = $Pose.pin_x - $anchor[0]
    $offsetY = $Pose.pin_y - $anchor[1]
    $result = @{}
    foreach ($name in $jointNames) {
        $result[$name] = @(($joints[$name][0] + $offsetX), ($joints[$name][1] + $offsetY))
    }
    return $result
}

function Test-Reference {
    param([AllowNull()]$Reference, [string]$Location, [hashtable]$TemplateNames, [hashtable]$ApparatusNames)
    $templateName = $null
    $apparatusOverride = $null
    if ($Reference -is [string]) { $templateName = [string]$Reference }
    elseif (Test-JsonObject $Reference) {
        if (-not (Has-Property $Reference "template") -or -not ($Reference.template -is [string])) {
            Add-ValidationError "$Location must contain a string template"
            return
        }
        $templateName = [string]$Reference.template
        if (Has-Property $Reference "apparatus") {
            if (-not (Test-JsonArray $Reference.apparatus)) { Add-ValidationError "$Location.apparatus must be an array" }
            else { $apparatusOverride = @($Reference.apparatus) }
        }
    }
    else {
        Add-ValidationError "$Location must be a template name or reference object"
        return
    }
    if ([string]::IsNullOrWhiteSpace($templateName)) { Add-ValidationError "$Location template must not be blank" }
    elseif (-not $TemplateNames.ContainsKey($templateName)) { Add-ValidationError "$Location references unknown template '$templateName'" }
    if ($null -ne $apparatusOverride) {
        $seen = @{}
        for ($index = 0; $index -lt $apparatusOverride.Count; $index++) {
            $name = $apparatusOverride[$index]
            if (-not ($name -is [string]) -or [string]::IsNullOrWhiteSpace([string]$name)) {
                Add-ValidationError "$Location.apparatus[$index] must be a non-blank string"
                continue
            }
            if ($seen.ContainsKey([string]$name)) { Add-ValidationError "$Location.apparatus repeats '$name'" }
            $seen[[string]$name] = $true
            if (-not $ApparatusNames.ContainsKey([string]$name)) { Add-ValidationError "$Location.apparatus references unknown apparatus '$name'" }
        }
    }
}

# ---------------------------------------------------------------------------------------------
$animationResolved = Resolve-ProjectPath $AnimationPath
if (Test-Path -LiteralPath $animationResolved -PathType Leaf) {
    $rawAnimation = Get-Content -Raw -LiteralPath $animationResolved -Encoding UTF8
    if ($rawAnimation -match '(?i)https?://|\bvimeo\b|\byoutu(?:be)?\b|"[^"\r\n]*(?:video|video_id|video_url)[^"\r\n]*"\s*:') {
        Add-ValidationError "Animation JSON must not contain video URLs, providers, or video-ID fields"
    }
}

$animation = Read-JsonFile $AnimationPath "Animation asset"
$movementLibrary = Read-JsonFile $MovementLibraryPath "Movement library"
$forensicExport = Read-JsonFile $ForensicExportPath "Forensic export"

$canvasWidth = 200.0
$canvasHeight = 120.0
$segments = $null
$poseCount = 0
$templateCount = 0
$keyframeCount = 0

if ($null -ne $animation) {
    if (-not (Test-JsonObject $animation)) { Add-ValidationError "Animation asset root must be an object" }

    if (-not (Has-Property $animation "schema_version") -or -not (Test-JsonNumber $animation.schema_version) -or [double]$animation.schema_version -ne 2.0) {
        Add-ValidationError "Animation asset .schema_version must be the supported value 2"
    }
    if (-not (Has-Property $animation "coordinate_system") -or [string]$animation.coordinate_system -cne "canvas_units_top_left") {
        Add-ValidationError "Animation asset .coordinate_system must be 'canvas_units_top_left'"
    }
    if (-not (Has-Property $animation "canvas") -or -not (Test-JsonObject $animation.canvas) -or
        -not (Test-FiniteNumber $animation.canvas.width) -or -not (Test-FiniteNumber $animation.canvas.height) -or
        [double]$animation.canvas.width -lt 50 -or [double]$animation.canvas.height -lt 30) {
        Add-ValidationError "Animation asset .canvas must define numeric width (>= 50) and height (>= 30)"
    }
    else {
        $canvasWidth = [double]$animation.canvas.width
        $canvasHeight = [double]$animation.canvas.height
    }

    $segmentNames = @("head_radius", "neck", "torso", "upper_arm", "forearm", "thigh", "shin", "foot")
    if (-not (Has-Property $animation "figure") -or -not (Test-JsonObject $animation.figure) -or -not (Test-JsonObject $animation.figure.segments)) {
        Add-ValidationError "Animation asset .figure.segments must be an object"
    }
    else {
        $segments = @{}
        foreach ($name in $segmentNames) {
            $value = $animation.figure.segments.$name
            if (-not (Test-FiniteNumber $value) -or [double]$value -lt 0.5 -or [double]$value -gt 60) {
                Add-ValidationError "figure.segments.$name must be a number from 0.5 through 60"
                $segments = $null
                break
            }
            $segments[$name] = [double]$value
        }
    }

    $requiredRootObjects = @("palette", "apparatus", "poses", "templates", "movements", "aliases", "category_defaults", "equipment_defaults")
    foreach ($field in $requiredRootObjects) {
        if (-not (Has-Property $animation $field) -or -not (Test-JsonObject $animation.$field)) {
            Add-ValidationError "Animation asset .$field must be an object"
        }
    }
    if (-not (Has-Property $animation "name_heuristics") -or -not (Test-JsonArray $animation.name_heuristics)) {
        Add-ValidationError "Animation asset .name_heuristics must be an array"
    }
    if (-not (Has-Property $animation "fallback") -or -not ($animation.fallback -is [string]) -or [string]::IsNullOrWhiteSpace([string]$animation.fallback)) {
        Add-ValidationError "Animation asset .fallback must be a non-blank template name"
    }

    if (Test-JsonObject $animation.palette) {
        foreach ($key in @("background", "border", "figure", "accent", "apparatus", "floor")) {
            if (-not (Has-Property $animation.palette $key) -or -not ($animation.palette.$key -is [string]) -or
                ([string]$animation.palette.$key -notmatch '^#(?:[0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$')) {
                Add-ValidationError "palette.$key must be a #RRGGBB or #AARRGGBB color"
            }
        }
    }

    # ---- apparatus ----
    $apparatusNames = @{}
    if (Test-JsonObject $animation.apparatus) {
        foreach ($property in Get-Properties $animation.apparatus) {
            $name = [string]$property.Name
            $apparatusNames[$name] = $true
            $primitives = $property.Value
            if (-not (Test-JsonArray $primitives) -or @($primitives).Count -eq 0) {
                Add-ValidationError "apparatus.$name must be a non-empty array"
                continue
            }
            for ($primitiveIndex = 0; $primitiveIndex -lt @($primitives).Count; $primitiveIndex++) {
                $primitive = @($primitives)[$primitiveIndex]
                $location = "apparatus.$name[$primitiveIndex]"
                if (-not (Test-JsonObject $primitive)) { Add-ValidationError "$location must be an object"; continue }
                $type = [string]$primitive.type
                $expectedValueCount = switch ($type) {
                    "line" { 4 }
                    "rect" { 4 }
                    "round_rect" { 4 }
                    "circle" { 3 }
                    "polyline" { -1 }
                    default { 0 }
                }
                if ($expectedValueCount -eq 0) { Add-ValidationError "$location.type '$type' is unsupported" }
                if ((Has-Property $primitive "role") -and
                    (-not ($primitive.role -is [string]) -or [string]$primitive.role -notin @("apparatus", "floor", "accent"))) {
                    Add-ValidationError "$location.role must be 'apparatus', 'floor', or 'accent'"
                }
                if (-not (Has-Property $primitive "values") -or -not (Test-JsonArray $primitive.values)) {
                    Add-ValidationError "$location.values must be an array"
                    continue
                }
                $values = @($primitive.values)
                if ($expectedValueCount -gt 0 -and $values.Count -ne $expectedValueCount) {
                    Add-ValidationError "$location.values must contain exactly $expectedValueCount numbers"
                }
                if ($expectedValueCount -eq -1 -and ($values.Count -lt 4 -or $values.Count % 2 -ne 0)) {
                    Add-ValidationError "$location.values must contain at least two coordinate pairs"
                }
                for ($valueIndex = 0; $valueIndex -lt $values.Count; $valueIndex++) {
                    $value = $values[$valueIndex]
                    if (-not (Test-FiniteNumber $value)) { Add-ValidationError "$location.values[$valueIndex] must be finite numeric data"; continue }
                    $isRadius = ($type -eq "circle" -and $valueIndex -eq 2)
                    $limit = if ($isRadius) { [math]::Min($canvasWidth, $canvasHeight) } elseif ($valueIndex % 2 -eq 0) { $canvasWidth } else { $canvasHeight }
                    if ([double]$value -lt 0.0 -or [double]$value -gt $limit) {
                        Add-ValidationError "$location.values[$valueIndex] must lie inside the canvas (0 through $limit)"
                    }
                }
                if ((Has-Property $primitive "filled") -and -not ($primitive.filled -is [bool])) { Add-ValidationError "$location.filled must be boolean" }
            }
        }
        if ($apparatusNames.Count -eq 0) { Add-ValidationError "Animation asset must define apparatus" }
    }

    # ---- poses (with base inheritance) ----
    $resolvedPoses = @{}
    if (Test-JsonObject $animation.poses) {
        $poseJson = @{}
        foreach ($property in Get-Properties $animation.poses) { $poseJson[[string]$property.Name] = $property.Value }
        $poseCount = $poseJson.Count
        if ($poseCount -eq 0) { Add-ValidationError "Animation asset must define at least one reusable pose" }

        function Resolve-PoseNamed {
            param([string]$Name, [string[]]$Stack)
            if ($resolvedPoses.ContainsKey($Name)) { return $resolvedPoses[$Name] }
            $json = $poseJson[$Name]
            $location = "poses.$Name"
            if ([string]::IsNullOrWhiteSpace($Name) -or -not (Test-JsonObject $json)) {
                Add-ValidationError "$location must be a named object"
                return $null
            }
            if ($Name -in $Stack) {
                Add-ValidationError "$location has a base inheritance cycle"
                return $null
            }
            $pose = New-Pose
            if (Has-Property $json "base") {
                $baseName = [string]$json.base
                if (-not $poseJson.ContainsKey($baseName)) {
                    Add-ValidationError "$location.base references unknown pose '$baseName'"
                }
                else {
                    $base = Resolve-PoseNamed $baseName ($Stack + $Name)
                    if ($null -ne $base) { $pose = Copy-Pose $base }
                }
            }
            Apply-PoseJson $pose $json $location
            $resolvedPoses[$Name] = $pose
            return $pose
        }
        foreach ($name in @($poseJson.Keys)) { Resolve-PoseNamed $name @() | Out-Null }
    }

    # ---- templates ----
    $templateNames = @{}
    if (Test-JsonObject $animation.templates) {
        foreach ($property in Get-Properties $animation.templates) { $templateNames[[string]$property.Name] = $true }
        $templateCount = $templateNames.Count
        if ($templateCount -eq 0) { Add-ValidationError "Animation asset must define at least one template" }

        foreach ($property in Get-Properties $animation.templates) {
            $name = [string]$property.Name
            $template = $property.Value
            $location = "templates.$name"
            if ([string]::IsNullOrWhiteSpace($name) -or -not (Test-JsonObject $template)) {
                Add-ValidationError "$location must be a named object"
                continue
            }
            if (-not (Has-Property $template "duration_ms") -or -not (Test-FiniteNumber $template.duration_ms) -or
                [double]$template.duration_ms % 1 -ne 0 -or [double]$template.duration_ms -lt 500 -or [double]$template.duration_ms -gt 8000) {
                Add-ValidationError "$location.duration_ms must be an integer from 500 through 8000"
            }
            $mode = [string]$template.mode
            if ($mode -notin @("ping_pong", "loop")) { Add-ValidationError "$location.mode must be 'ping_pong' or 'loop'" }
            if ((Has-Property $template "easing") -and [string]$template.easing -notin @("smooth", "linear")) {
                Add-ValidationError "$location.easing must be 'smooth' or 'linear'"
            }
            if ((Has-Property $template "reduced_motion_frame") -and
                (-not (Test-FiniteNumber $template.reduced_motion_frame) -or [double]$template.reduced_motion_frame -lt 0.0 -or [double]$template.reduced_motion_frame -gt 1.0)) {
                Add-ValidationError "$location.reduced_motion_frame must be normalized from 0 through 1"
            }
            if (-not (Has-Property $template "apparatus") -or -not (Test-JsonArray $template.apparatus)) {
                Add-ValidationError "$location.apparatus must be an array"
            }
            else {
                $seenTemplateApparatus = @{}
                for ($index = 0; $index -lt @($template.apparatus).Count; $index++) {
                    $apparatusName = @($template.apparatus)[$index]
                    if (-not ($apparatusName -is [string]) -or [string]::IsNullOrWhiteSpace([string]$apparatusName)) {
                        Add-ValidationError "$location.apparatus[$index] must be a non-blank string"
                        continue
                    }
                    if ($seenTemplateApparatus.ContainsKey([string]$apparatusName)) { Add-ValidationError "$location.apparatus repeats '$apparatusName'" }
                    $seenTemplateApparatus[[string]$apparatusName] = $true
                    if (-not $apparatusNames.ContainsKey([string]$apparatusName)) { Add-ValidationError "$location.apparatus references unknown apparatus '$apparatusName'" }
                }
            }
            if (-not (Has-Property $template "keyframes") -or -not (Test-JsonArray $template.keyframes) -or @($template.keyframes).Count -lt 1) {
                Add-ValidationError "$location.keyframes must contain at least one frame"
                continue
            }
            $lastAt = -1.0
            $frameTotal = @($template.keyframes).Count
            for ($frameIndex = 0; $frameIndex -lt $frameTotal; $frameIndex++) {
                $frame = @($template.keyframes)[$frameIndex]
                $frameLocation = "$location.keyframes[$frameIndex]"
                if (-not (Test-JsonObject $frame)) { Add-ValidationError "$frameLocation must be an object"; continue }
                if (-not (Has-Property $frame "at") -or -not (Test-FiniteNumber $frame.at) -or [double]$frame.at -lt 0.0 -or [double]$frame.at -gt 1.0) {
                    Add-ValidationError "$frameLocation.at must be finite and normalized from 0 through 1"
                }
                else {
                    $at = [double]$frame.at
                    if ($at -le $lastAt) { Add-ValidationError "$frameLocation.at must be strictly greater than the preceding keyframe" }
                    $lastAt = $at
                    if ($frameIndex -eq 0 -and $at -ne 0.0) { Add-ValidationError "$location must begin with a keyframe at 0" }
                    if ($frameIndex -eq $frameTotal - 1 -and $at -ne 1.0) { Add-ValidationError "$location must end with a keyframe at 1" }
                }
                $pose = New-Pose
                $hasBody = $false
                if (Has-Property $frame "pose") {
                    if (-not ($frame.pose -is [string]) -or [string]::IsNullOrWhiteSpace([string]$frame.pose)) {
                        Add-ValidationError "$frameLocation.pose must be a non-blank string"
                    }
                    elseif (-not $resolvedPoses.ContainsKey([string]$frame.pose)) {
                        Add-ValidationError "$frameLocation.pose references unknown pose '$($frame.pose)'"
                    }
                    else {
                        $pose = Copy-Pose $resolvedPoses[[string]$frame.pose]
                        $hasBody = $true
                    }
                }
                elseif (Has-Property $frame "torso") { $hasBody = $true }
                if (-not $hasBody) { Add-ValidationError "$frameLocation must reference a pose or define torso and limb angles"; continue }
                Apply-PoseJson $pose $frame $frameLocation
                $keyframeCount++

                # Forward kinematics: every joint must remain inside the canvas (small margin for stroke width).
                if ($null -ne $segments) {
                    $solvedJoints = Solve-Pose $pose $segments
                    $margin = 6.0
                    foreach ($jointName in $jointNames) {
                        $point = $solvedJoints[$jointName]
                        if ($point[0] -lt -$margin -or $point[0] -gt $canvasWidth + $margin -or $point[1] -lt -$margin -or $point[1] -gt $canvasHeight + $margin) {
                            Add-ValidationError ("$frameLocation places joint '$jointName' outside the canvas at ({0:N1}, {1:N1})" -f $point[0], $point[1])
                        }
                    }
                }
            }
        }
    }

    if ((Has-Property $animation "fallback") -and $animation.fallback -is [string] -and -not $templateNames.ContainsKey([string]$animation.fallback)) {
        Add-ValidationError "fallback references unknown template '$($animation.fallback)'"
    }

    foreach ($mappingName in @("movements", "aliases", "category_defaults", "equipment_defaults")) {
        $mapping = $animation.$mappingName
        if (-not (Test-JsonObject $mapping)) { continue }
        $normalizedKeys = @{}
        foreach ($property in Get-Properties $mapping) {
            $normalized = Normalize-Key ([string]$property.Name)
            if ([string]::IsNullOrWhiteSpace($normalized)) { Add-ValidationError "$mappingName contains a blank normalized key" }
            elseif ($normalizedKeys.ContainsKey($normalized)) { Add-ValidationError "$mappingName has keys that collide after normalization: '$($property.Name)'" }
            else { $normalizedKeys[$normalized] = [string]$property.Name }
            Test-Reference $property.Value "$mappingName.$($property.Name)" $templateNames $apparatusNames
        }
    }

    if (Test-JsonArray $animation.name_heuristics) {
        for ($index = 0; $index -lt @($animation.name_heuristics).Count; $index++) {
            $heuristic = @($animation.name_heuristics)[$index]
            $location = "name_heuristics[$index]"
            if (-not (Test-JsonObject $heuristic)) { Add-ValidationError "$location must be an object"; continue }
            if (-not (Has-Property $heuristic "contains") -or -not (Test-JsonArray $heuristic.contains) -or @($heuristic.contains).Count -eq 0) {
                Add-ValidationError "$location.contains must be a non-empty array"
            }
            else {
                for ($tokenIndex = 0; $tokenIndex -lt @($heuristic.contains).Count; $tokenIndex++) {
                    $token = @($heuristic.contains)[$tokenIndex]
                    if (-not ($token -is [string]) -or [string]::IsNullOrWhiteSpace((Normalize-Key ([string]$token)))) {
                        Add-ValidationError "$location.contains[$tokenIndex] must be a meaningful string"
                    }
                }
            }
            if (-not (Has-Property $heuristic "animation")) { Add-ValidationError "$location.animation is required" }
            else { Test-Reference $heuristic.animation "$location.animation" $templateNames $apparatusNames }
        }
    }

    if ($null -ne $movementLibrary -and (Test-JsonArray $movementLibrary.movements) -and (Test-JsonObject $animation.movements)) {
        $expectedMovementIds = @($movementLibrary.movements | ForEach-Object { [string]$_.id })
        if ($expectedMovementIds.Count -ne 115) {
            Add-ValidationError "Authoritative movement library must contain exactly 115 movements, found $($expectedMovementIds.Count)"
        }
        foreach ($duplicate in @($expectedMovementIds | Group-Object | Where-Object Count -gt 1)) {
            Add-ValidationError "Movement library repeats ID '$($duplicate.Name)'"
        }
        $actualMovementIds = @((Get-Properties $animation.movements) | ForEach-Object { [string]$_.Name })
        foreach ($missing in @($expectedMovementIds | Where-Object { $_ -notin $actualMovementIds })) {
            Add-ValidationError "movements is missing authoritative ID '$missing'"
        }
        foreach ($extra in @($actualMovementIds | Where-Object { $_ -notin $expectedMovementIds })) {
            Add-ValidationError "movements contains unknown ID '$extra'"
        }
        if ($actualMovementIds.Count -ne 115) {
            Add-ValidationError "Animation asset must map exactly 115 movement IDs, found $($actualMovementIds.Count)"
        }
    }
    elseif ($null -ne $movementLibrary) { Add-ValidationError "Cannot verify exact movement coverage" }

    if ($null -ne $forensicExport -and (Test-JsonObject $animation.aliases)) {
        $authenticNames = @(
            $forensicExport.levels.programs |
                Where-Object { [bool]$_.has_details } |
                ForEach-Object { @($_.exercises) } |
                ForEach-Object { [string]$_.name.en } |
                Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
                Sort-Object -Unique
        )
        if ($authenticNames.Count -ne 34) {
            Add-ValidationError "Forensic export must expose exactly 34 distinct authentic English exercise names, found $($authenticNames.Count)"
        }
        $aliasKeys = @{}
        foreach ($property in Get-Properties $animation.aliases) { $aliasKeys[(Normalize-Key ([string]$property.Name))] = $true }
        foreach ($authenticName in $authenticNames) {
            $normalizedName = Normalize-Key $authenticName
            if (-not $aliasKeys.ContainsKey($normalizedName)) {
                Add-ValidationError "aliases is missing authentic exercise name '$authenticName' ('$normalizedName')"
            }
        }
    }
    elseif ($null -ne $forensicExport) { Add-ValidationError "Cannot verify authentic exercise-name aliases" }
}

if ($errors.Count -gt 0) {
    Write-Host "Animation validation failed with $($errors.Count) error(s):" -ForegroundColor Red
    foreach ($validationError in $errors) { Write-Host " - $validationError" -ForegroundColor Red }
    exit 1
}

$movementCount = if ($null -ne $animation -and (Test-JsonObject $animation.movements)) { (Get-Properties $animation.movements).Count } else { 0 }
$aliasCount = if ($null -ne $animation -and (Test-JsonObject $animation.aliases)) { (Get-Properties $animation.aliases).Count } else { 0 }
Write-Host "Animation validation passed: $poseCount poses, $templateCount templates ($keyframeCount keyframes solved inside the canvas), $movementCount movement mappings, $aliasCount aliases; offline JSON contains no video references."

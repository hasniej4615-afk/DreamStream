
$html = Get-Content main_malaysia.html -Raw
$count = ([regex]::Matches($html, "(?i)class=`"[^`"]*menu-item[^`"]*`"")).Count
Write-Output "Menu items: $count"

